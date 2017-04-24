(ns directionalsurvey.schema
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
            [com.walmartlabs.lacinia.schema :as schema]
            [directionalsurvey.db :as db]))

(def star-wars-schema
  (-> (io/resource "directionalsurveyschema.edn")
      slurp
      edn/read-string
      (attach-resolvers {:resolve-users          db/resolve-users
                         :resolve-actions        db/resolve-actions})
      schema/compile))