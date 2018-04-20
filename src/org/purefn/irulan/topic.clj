(ns org.purefn.irulan.topic
  "Common utilities and specs for working with Kafka topics."
  (:require [clojure.spec.alpha :as s]
            [org.purefn.irulan.registry :as registry]))

;; Topic name spec is derived from
;; https://stackoverflow.com/questions/37062904/what-are-apache-kafka-topic-name-limitations
(def topic-name-re #"[a-zA-Z0-9\\._\\-]")
(s/def ::name (s/and string?
                     #(< (count %) 249)
                     #(re-matches topic-name-re %)))

(def spec? (partial satisfies? s/Spec))
(s/def ::spec (s/or :set (s/and set? (s/coll-of keyword?))
                    :spec spec?))

(def default-num-partitions 24)

(defn declare-topic
  "Registers a topic using its name, a docstring describing it, a spec for
   all possible contents of this topic,the partitioning key, and the number of
   partitions Kafka should create. This information will be stored in the
   registry similarly to how events and commands are currently storing their
   specs and metadata.

   `name` - the string that will be used as the topic name.

   `doc` - a plain english description of what the topic will be used for and
   what kinds of things can be found in this topic.

   `spec` - either a set or a clojure spec that specifies the keyword types of
   all the commands or events that may be found in this topic.

   `partition-key` - the keyword that will get extracted out of the command or
    event's payload to be used as the topic's partitioning key.

   `partitions` - (optional) number of partitions needed for this topic."
  ([name doc spec partition-key]
   (declare-topic name doc spec partition-key default-num-partitions))
  ([name doc spec partition-key partitions]
   (registry/register-topic name doc spec partition-key partitions)
   nil))

(s/fdef declare-topic
        :args (s/cat :name ::name
                     :doc string?
                     :spec ::spec
                     :partition-key keyword?
                     :partitions pos-int?)
        :ret any?)

(defn topics-by-message-type
  "View of the registry in a map like 
    message-type -> {topic {meta-data}}, topic {meta-data} ...}"
  []
  (reduce (fn [m [topic {:keys [spec] :as meta-data}]]
            (let [t (into {} (map (juxt identity (constantly {topic meta-data})))
                          spec)]
              (merge-with into m t)))
          {}
          (:topic (registry/registry))))

(defn assert-topic
  "Determines if an event is valid for the given topic.

  If valid, returns `event-type`.
  If not, throws an exception."
  [topic event-type]
  (let [topic-spec (get-in (registry/registry) [:topic topic :spec])]
    (if (s/valid? topic-spec event-type)
      event-type
      (throw (ex-info (format "%s not permitted in topic %s with spec %s"
                              event-type topic topic-spec)
                      {:cause :topic-spec
                       :input {:topic topic
                               :event-type event-type}})))))
