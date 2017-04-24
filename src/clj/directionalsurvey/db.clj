(ns directionalsurvey.db
  (:require
    [datomic.api :as d :refer [db q]]
    [clojure.tools.logging :as log]
    [com.walmartlabs.lacinia.schema :as schema]))

(defn now [] (new java.util.Date))

;datomic setup
(defn create-db [url]
  (d/create-database url)
  (let [schema (read-string (slurp "resources/directionalsurvey.edn"))
        conn (d/connect url)]
    (d/transact conn schema)
    {:db-connection conn
     :change-queue (d/tx-report-queue conn)}))

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

(defn resolve-mutate-user
  [ctx args value]
  (let [name      (:name args)
        password  (:password args)
        db-connection (:db-connection @datastore)
        tmp       @(d/transact db-connection
                               [{:db/id #db/id[:db.part/user]
                                 :user/name name
                                 :user/password password}])
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

(defn resolve-mutate-action
  [ctx args value]
  (let [username (:user args)
        rowIdx (:row args)
        colIdx (:col args)
        newVal (:val args)
        db-connection (:db-connection @datastore)
        tmp  @(d/transact db-connection
                          [{:db/id #db/id[:db.part/user]
                            :action/user     username
                            :action/row      rowIdx
                            :action/column   colIdx
                            :action/newval   newVal
                            :action/instant  (now)}])
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








