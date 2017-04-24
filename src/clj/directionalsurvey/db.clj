(ns directionalsurvey.db
  (:require
    [datomic.api :as d :refer [db q]]
    [clojure.tools.logging :as log]
    [com.walmartlabs.lacinia.schema :as schema]))

(def datastore (atom {:db-connection nil
                       :change-queue nil}))

(defn resolve-users
  [ctx args value]
  (let [db-connection (:db-connection @datastore)
        mydb (d/db db-connection)
        rawdata (q '[:find [(pull ?e [:user/name :user/password]) ...]
                     :where [?e :user/name]]
                   mydb)
        tmpusers (mapv (fn [in]
                          {:name (:user/name in)
                           :password  (:user/password in)}) rawdata)
        users (map #(schema/tag-with-type % :user) tmpusers)]
    (log/info users)
    users))

(defn resolve-actions
  [ctx args value]
  (let [db-connection (:db-connection @datastore)
        mydb (d/db db-connection)
        rawdata (q '[:find [(pull ?e [:action/user :action/row :action/column :action/newval :action/instant]) ...]
                     :where [?e :action/user]]
                   mydb)
        tmpactions (mapv (fn [in]
                           {:user (:action/user in)
                            :row  (:action/row in)
                            :col  (:action/column in)
                            :val  (:action/newval in)
                            :inst (:action/instant in)}) rawdata)
        actions (map #(schema/tag-with-type % :action) tmpactions)]
    (log/info actions)
    actions))






