(ns org.purefn.irulan.command
  "Common utilities and specs for all commands."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]

            [org.purefn.irulan.common :as common]
            [org.purefn.irulan.registry :as registry]))

;; TODO: also need some command-response specs

;;; Commands

;; Like events, commands need an id and a type-specific payload. We
;; also want something sort of like provenance to know where and who it came
;; from.


;; Things we want to know about the source of the command:
;; ip address/host
;; username
;; time
;; process - useful for nested command flows


;; I prefer "source" to "provenance" for the name of this concept as
;; it applies to commands, as commands are on a somewhat lesser
;; existential "footing" than events.


;;-------------------------------------------------------
;; Command Source
;;-------------------------------------------------------

;; The hostname or ip address of who is issuing this command. This is
;; particularly relevant for commands that originate from the outside
;; world.
(s/def ::host common/host?)

;; Username that issued this command
(s/def ::username string?)

;; Command id
(s/def ::id uuid?)

;; When we got this command
(s/def ::received-at common/timestamp?)

;; id of the process this command is a part of, for use in multi-step
;; or multi-level command flows. This will generally either be the id
;; of the parent command or its process id.
(s/def ::process-id uuid?)

;; id which locates the resource which will receive the response to this command
(s/def ::response-id uuid?)

;; The command source
(s/def ::source (s/keys :opt [::username ::host]))

;;-------------------------------------------------------
;; Command
;;-------------------------------------------------------

;; dispatch on the ::command-type key in the payload
(defmulti command-type ::command-type)

;; the payload multi-spec, with dispatch on ::command-type
(s/def ::payload (s/multi-spec command-type ::command-type))

(s/def ::command (s/keys :req [::payload ::id]
                         :opt [::source ::response-id ::process-id]))

;;; defsimple

;; do we want to give it a name? if so, what would the name do?
;; construction, like the event one? nah, that seems silly. No one
;; needs to construct these things. The other option is that it will
;; run the function. I like that.


(defmacro defsimple
  "Defines a command that can be processed by the given `handler`
   function. What makes the command simple is that he processing can
   be done statelessly, so the handler is a pure function of
   command -> [event-payload].

   Additionally, there is no business logic applied to simple commands
   that could result in rejection, outside of validation. For this
   reason, simple commands don't generate command responses.

   One typical use case is to process webhook- or callback-style
   requests originating from outside our system, where we don't have
   control over the request format and a response isn't necessary.

   The provided `type`, a namespace-qualified keyword, will be used to
   identify the command in the registry.

   Simple commands will be validated by the provided `spec`, which
   doesn't necessarily conform to the spec for regular commands.

   It also defn's `name` to the handler function, after passing the
   input through spec validation.

   A `web-handler` may also be supplied.  Since simple commands sit 
   on the boundary of our system and may take input from external systems,
   it may desirable to pre-process a raw ring request into input suitable
   for `handler`.  This fn should take a ring request map as an argument
   and return input for `handler`.
  
   Finally, it takes a set of the possible events it generates."
  {:arglists '([name docstring? type spec handler web-handler generates])}
  [name & params]
  (let [[doc type spec handler web-handler generates]
        (if (string? (first params))
          params
          (cons nil params))]
    `(do
       (s/def ~type ~spec)
       (defn ~name
         ~(or doc "")
         [command#]
         (if (s/valid? ~type command#)
           (~handler command#)
           (throw (ex-info (s/explain-str ~type command#)
                           {:input command#
                            :cause :command-spec}))))
       (registry/register-command ~type ~spec
                                  :doc ~doc :handler ~name
                                  :web-handler ~web-handler
                                  :generates ~generates))))

;; TODO: add spec to the function?
;; TODO: spec the macro

;;-------------------------------------------------------
;; defcommand
;;-------------------------------------------------------

(defn- command-generator
   "Takes a map of {command-type spec}, and returns a generator that
   will generate commands matching one of the specs with a matching
   `::command-type`."
  [typespecs]
  (let [types (set (keys typespecs))]
    (gen/hash-map
     ::source (s/gen ::source)
     ::id (s/gen ::id)
     ::payload (->> (gen/bind (s/gen types)
                              (fn [type]
                                (gen/tuple (s/gen (get types type))
                                           (gen/return type))))
                    (gen/fmap (fn [[payload type]]
                                (assoc payload ::command-type type)))))))

(defn command-spec
  "Returns a composite command spec for a given collection of versions,
   specified as maps with :type and :spec keys."
  [versions]
  (let [typespecs (into {} (map (juxt :type :spec) versions))
        types (set (keys typespecs))]
    (s/with-gen
      (s/and ::command
             #(contains? types (get-in % [::payload ::command-type])))
      #(command-generator typespecs))))

(defn command-version
  "Returns the versioned command type for given version number and base type.

   (command-version 1 :recruiter.profile/create-profile)
     => :recruiter.profile.command.v1/create-profile"
  [v type]
  (keyword (str (namespace type) ".command.v" v) (name type)))

(defn unversioned-command
  "Given a versioned command type returns the unversioned base-type as a keyword

  (unversiond-command :recruiter.profile.command.v1/create-profile)
    => :recruiter.profile/create-profile"
  [versioned]
  (-> (str versioned)
      (str/replace #":" "")
      (str/replace #"\.command\.v\d\/" "/")
      (keyword)))

(defmacro defcommand
  "Defines a new command type, with constructor bound to given `name`
   var and optional docstring. The constructor is a function with two
   arities: version number and version-specific payload (minus
   `::command-type` key); or version number, source map, and an
   payload. The former arity returns just the payload with correct
   versioned `::command-type`, the latter returns the complete command.

   `command-type` is a namespaced keyword used as the basis for
   constructing version-specific type dispatch values as well as the
   spec for the command itself.
  
   `version` is a version spec, defining a version of the command (a
   non-negative integer). For every command version other than the
   lowest, functions to up- and down- cast from/to the previous
   version must be supplied. One or more versions can be defined.

   (version n docstring? spec)
   (version n docstring? spec :up up-fn :down down-fn)

  `web-handler` is an optional transformation function which will be applied
   by the gatekeeper before attempting to validate the command spec.

   (web-handler identity)"
  {:arglists '([name docstring? command-type response-type web-handler? version*])}
  [name & params]
  (let [[doc command-type response-type & opts] (if (string? (first params))
                                                      params
                                                      (cons nil params))

        versions (filter (comp (partial = 'version) first) opts)
        web-handler (->> opts
                         (filter (comp (partial = 'web-handler) first))
                         (map second)
                         (first)) 

        version-specs (map
                       (fn [[_ n & params]]
                         (let [[doc spec & params] (if (string? (first params))
                                                     params
                                                     (cons nil params))
                               casters (-> (apply hash-map params)
                                           (select-keys [:up :down]))]
                           (merge casters
                                  {:type (command-version n command-type)
                                   :version n
                                   :doc doc
                                   :spec spec})))
                       versions)
 
        versions-code
        (mapcat (fn [{:keys [spec type version doc up down] :as vs}]
                  (list
                   `(defmethod command-type ~type
                      [~'_]
                      ~spec)
                   `(s/def ~(command-version version command-type) ~spec)
                   `(registry/register-command-version ~command-type ~version
                                                       ~spec
                                                       :doc ~doc
                                                       :upcast ~up
                                                       :downcast ~down)))
                version-specs)]
    `(do
       (let [spec# (command-spec (list ~@version-specs))]
         (s/def ~command-type spec#)
         (registry/register-command ~command-type spec#
                                    :web-handler ~web-handler
                                    :doc ~doc
                                    :response ~response-type))
       ~@versions-code
       (defn ~name
         {:arglists '([version payload] [version source payload])
          :doc ~(or doc "")}
         ([version# payload#]
          {::id (java.util.UUID/randomUUID)
           ::payload (assoc payload#
                            ::command-type (command-version version# ~command-type))})
         ([version# source# payload#]
          (assoc (~name version# payload#) ::source source#))))))

(defn assert-command
  "Determines if a command is valid.

  If valid, returns `command`.
  If not, throws an excpetion."
  ([command]
   (if-let [command-type (-> command ::payload ::command-type)]
     (assert-command (unversioned-command command-type) command)
     (throw (ex-info "No ::command-type found in payload"
                     {:input command
                      :cause ::command-type}))))
  ([command-type command]
   (if (s/valid? command-type command)
     command
     (throw (ex-info (s/explain-str command-type command)
                     {:input command
                      :cause ::spec})))))
