(ns user
  (:require [clojure.tools.namespace.repl :refer :all]))

(defn dev
  "Load and switch to the 'dev' namespace."
  []
  (require 'dev)
  (in-ns 'dev))
