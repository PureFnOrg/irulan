(ns org.purefn.irulan.view
  (:require [org.purefn.irulan.registry :as registry]))

(defn defview
  "Registers a kafka streams view with the given type and using the given
   kubernetes service name."
  ([type service]
   (registry/register-view type service 8000))
  ([type service port]
   (registry/register-view type service port)))
