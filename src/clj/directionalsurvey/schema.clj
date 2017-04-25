(ns directionalsurvey.schema
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
            [com.walmartlabs.lacinia.schema :as schema]
            [directionalsurvey.db :as db]))

(def directional-survey-schema
  (-> (io/resource "directionalsurveyschema.edn")
      slurp
      edn/read-string
      (attach-resolvers {:resolve-users          db/resolve-users
                         :resolve-actions        db/resolve-actions
                         :resolve-mutate-user    db/resolve-mutate-user
                         :resolve-mutate-action   db/resolve-mutate-action})
      schema/compile))