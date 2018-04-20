(ns org.purefn.irulan.registry)

;; Hey, this is the exact same way spec stores its registry, so it has
;; to be right.
(defonce ^:private registry-ref (atom {}))

(defn register-event
  "Adds event to the registry, with associated spec and optional doc."
  [type spec & [doc]]
  (swap! registry-ref assoc-in [:event type :base]
         {:spec spec
          :doc doc}))

(defn register-event-version
  [type version spec & {:keys [doc upcast downcast]}]
  (swap! registry-ref assoc-in [:event type version]
         {:spec spec
          :doc doc
          :upcast upcast
          :downcast downcast}))

(defn register-command
  [type spec & {:keys [doc handler generates web-handler response]}]
  (swap! registry-ref assoc-in [:command type]
         {:spec spec
          :response response
          :doc doc
          :web-handler web-handler
          :handler handler
          :generates generates}))

(defn register-command-version
  [type version spec & {:keys [doc upcast downcast]}]
  (swap! registry-ref assoc-in [:command type version]
         {:spec spec
          :doc doc
          :upcast upcast
          :downcast downcast}))

(defn register-topic
  "Adds topic information to registry, associates the name, spec,
   doc string, partitioning keyword, and the number of partitions."
  [name doc spec partition-key partitions]
  (swap! registry-ref assoc-in [:topic name]
         {:spec spec
          :doc doc
          :partition-key partition-key
          :partitions partitions}))

(defn register-view
  "Associates the service name using the given view type as the key in the registry."
  [type service port]
  (swap! registry-ref assoc-in [:view type]
         {:service service
          :port port}))

(defn register-query
  [name key-fn topics]
  (swap! registry-ref assoc-in [:query name]
         {:key-fn key-fn
          :topics topics}))

(defn registry
  "Returns current registry."
  []
  @registry-ref)
