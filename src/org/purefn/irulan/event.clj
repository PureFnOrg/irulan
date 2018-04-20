(ns org.purefn.irulan.event
  "Common utilities and specs for all events."
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [com.gfredericks.test.chuck.generators :refer [string-from-regex]]
            [org.purefn.irulan.common :as common]
            [org.purefn.irulan.registry :as registry]))

;;------------------------------------------------------------------------------
;; Provenance: the place of origin or earliest known history of something
;;------------------------------------------------------------------------------

;; This information is added automatically to add events stored in Kafka by the
;; event processing framework.  By analyzing this information we will be able
;; to reconstruct the history of event processing and measure performance across
;; the whole system.

;; The unique ID of this event.
(s/def ::id uuid?)

;; The unique IDs of events directly used to compute the value of this event.
(s/def ::derived-from (s/coll-of uuid? :min-count 1 :into #{} :distinct true))

;; The unique name of the event handler which created this event.
(s/def ::handler common/handler?)

;; The first 6 digits of the Git commit hash for the event handler.
(s/def ::commit common/commit?)

;; The hostname (or IP-address) on which the event handler was running when
;; it created this event.
(s/def ::host common/host?)

;; The number of milliseconds that elapsed between the time the event handler
;; consumned the most recent parent event and when this event was created.
(s/def ::span nat-int?)

;; All information about the event handler.
(s/def ::generated-by (s/keys :req [::handler ::host]
                              :opt [::commit ::span]))

;; The time stamp (milliseconds since 1970) of when this event was created.
(s/def ::created-at common/timestamp?)

;; Everything about the origins of this event.
(s/def ::provenance
  (s/keys :req [::created-at ::generated-by]
          :opt [::derived-from]))


;;------------------------------------------------------------------------------
;; Event.
;;------------------------------------------------------------------------------

;; A unique keyword identifying the spec associated with the event.
(s/def ::event-type keyword?)

;; A multi-method which chooses a spec based on the value of `::event-type` in
;; the `::payload` of the event.
(defmulti event-type ::event-type)

;; The specialized value payload of an event.  New events are specified by
;; defining a new instance of the `event-type` multi-method which returns the
;; spec for the particular payload required by the event.
(s/def ::payload (s/multi-spec event-type ::event-type))

;; A generic spec for all events, where the spec of the payload is determined
;; by the `::event-type`.
(s/def ::event (s/keys :req [::payload ::id]
                       :opt [::provenance]))


;; - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
;; Example Event Spec:
;;
;; (s/def ::name string?)
;; (s/def ::age int?)
;; (s/def ::gender #(::male ::female))
;;
;; (defmethod event-type ::person [_]
;;   (s/keys :req [::event-type ::name ::age] :opt [::gender]))
;;
;; - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

;; (defevent opened
;;   "A given email was opened by the recipient."
;;   :email.sendwithus/opened
;;   (version 1 "optional version specific docs" some-spec)
;;   (version 2 another-spec :up some-fn :down some-fn))

;; output:
;; generates function "opened", signature [version provenance body]
;; adds multimethods for each version
;; registers spec for each version
;; registers the docs and event

(defn- event-generator
   "Takes a map of {event-type spec}, and returns a generator that
   will generate events matching one of the specs with a matching
   `::event-type`."
  [typespecs]
  (let [types (set (keys typespecs))]
    (gen/hash-map
     ::id (s/gen ::id)
     ::provenance (s/gen ::provenance)
     ::payload (->> (gen/bind (s/gen types)
                              (fn [type]
                                (gen/tuple (s/gen (get types type))
                                           (gen/return type))))
                    (gen/fmap (fn [[payload type]]
                                (assoc payload ::event-type type)))))))

(defn event-spec
  "Returns a composite event spec for a given collection of versions,
   specified as maps with :type and :spec keys."
  [versions]
  (let [typespecs (into {} (map (juxt :type :spec) versions))
        types (set (keys typespecs))]
    (s/with-gen
      (s/and ::event
             #(contains? types (get-in % [::payload ::event-type])))
      #(event-generator typespecs))))

(defn event-version
  "Returns the versioned event type for given version number and base type.

   (event-version 1 :email.sendwithus/opened)
     => :email.sendwithus.event.v1/opened"
  [v type]
  (keyword (str (namespace type) ".event.v" v) (name type)))

(defn unversioned-event
  "Given a versioned command type returns the unversioned base-type as a keyword

  (unversiond-command :recruiter.profile.command.v1/create-profile)
    => :recruiter.profile/create-profile"
  [versioned]
  (-> (str versioned)
      (str/replace #":" "")
      (str/replace #"\.event\.v\d\/" "/")
      (keyword)))

(defn versioned-event-type?
  "Returns true if passed a keyword representing a versioned event
   type."
  [event-type]
  (when (keyword? event-type)
    (when-let [ns (namespace event-type)]
      (some? (re-find #"event\.v\d+\z" ns)))))

(defn destructured-event-type
   "Takes a versioned event type and returns a tuple of
   [base-type version]

   eg:
   (destructured-event-type :recruiter.profile.event.v1/profile-created)
     => [:recruiter.profile/profile-created 1]"
  [versioned-event-type]
  (when (versioned-event-type? versioned-event-type)
    (let [[_ base-type version]
          (re-find #"(.+)\.event\.v(\d+)\z" (namespace versioned-event-type))]
      [(keyword base-type (name versioned-event-type))
       (Integer. version)])))

;; what are we emitting:
;; - spec and registration for each version
;; - multimethod for each version
;; - generation function
;; - spec and registration for entire event

(defmacro defevent
  "Defines a new event type, with constructor bound to given `name`
   var and optional docstring. The constructor is a function with two
   arities: version number and version-specific payload (minus
   `::event-type` key); or version number, provenance map, and an
   payload. The former arity returns just the payload with correct
   versioned `::event-type`, the latter returns the complete event.

   `event-type` is a namespaced keyword used as the basis for
   constructing version-specific type dispatch values as well as the
   spec for the event itself.

   `version` is a version spec, defining a version of the event (a
   non-negative integer). For every event version other than the
   lowest, functions to up- and down- cast from/to the previous
   version must be supplied. One or more versions can be defined.

   (version n docstring? spec)
   (version n docstring? spec :up up-fn :down down-fn)"
  {:arglists '([name docstring? event-type version*])}
  [name & params]
  (let [[doc event-type & versions] (if (string? (first params))
                                      params
                                    (cons nil params))
        version-specs (map
                       (fn [[_ n & params]]
                         (let [[doc spec & params] (if (string? (first params))
                                                     params
                                                     (cons nil params))
                               casters (-> (apply hash-map params)
                                           (select-keys [:up :down]))]
                           (merge casters
                                  {:type (event-version n event-type)
                                   :version n
                                   :doc doc
                                   :spec spec})))
                       versions)

        versions-code
        (mapcat (fn [{:keys [spec type version doc up down]}]
                  (list
                   `(defmethod event-type ~type
                      [~(symbol "_")]
                      ~spec)
                   `(s/def ~(event-version version event-type) ~spec)
                   `(registry/register-event-version ~event-type ~version
                                                     ~spec
                                                     :doc ~doc
                                                     :upcast ~up
                                                     :downcast ~down)))
                version-specs)]
    `(do
       (let [spec# (event-spec (list ~@version-specs))]
         (s/def ~event-type spec#)
         (registry/register-event ~event-type spec# ~doc))
       ~@versions-code
       (defn ~name
         {:arglists '([version payload] [version provenance payload])
          :doc ~(or doc "")}
         ([version# payload#]
          {::id (java.util.UUID/randomUUID)
           ::payload (assoc payload#
                            ::event-type (event-version version# ~event-type))})
         ([version# provenance# payload#]
          (assoc (~name version# payload#) ::provenance provenance#))))))

(defn assert-event
  "Determines if an event is valid.

  If valid, returns `event.`
  If not, throws an exception."
  [event-type event]
  (if (s/valid? event-type event)
    event
    (throw (ex-info (s/explain-str event-type event)
                    {:input event
                     :cause :event-spec}))))

;; TODO: do some validation in the emitted function

(def ^:private fnable?
  "True if given form can be a fn if evaled."
  (s/or :kw keyword? :list list? :sym symbol?))

(s/def ::up fnable?)
(s/def ::down fnable?)

(s/fdef defevent
        :args (s/cat :name simple-symbol?
                     :docstring (s/? string?)
                     :event-type (s/and keyword? namespace)

                     :first-version
                     (s/spec
                      (s/cat :prefix #{'version}
                             :version nat-int?
                             :docstring (s/? string?)
                             :spec fnable?))

                     :other-versions
                     (s/* (s/spec
                           (s/cat :prefix #{'version}
                                  :version nat-int?
                                  :docstring (s/? string?)
                                  :spec fnable?
                                  :casters (s/keys* :req-un [::up ::down])))))
        :ret any?)

(s/fdef assert-event
        :args (s/cat :event-type keyword?
                     :event map?))
