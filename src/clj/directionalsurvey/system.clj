(ns directionalsurvey.system
  (:require [taoensso.sente :as sente]
            [clojure.tools.logging :as log]
            [datomic.api :as d :refer [db q]]))

(defn now [] (new java.util.Date))

;datomic setup
(defn create-db [url]
  (d/create-database url)
  (let [schema (read-string (slurp "resources/directionalsurvey.edn"))
        conn (d/connect url)]
    (d/transact conn schema)
    {:db-connection conn
     :change-queue (d/tx-report-queue conn)}))

;sente setup, This function will be called whenever a new channel is open
(defn- get-user-id [request] 
  (str (java.util.UUID/randomUUID))) ;; Random user

(def ws-connection (sente/make-channel-socket! {:user-id-fn get-user-id}))
(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      ws-connection]
  (def ring-ws-post ajax-post-fn)
  (def ring-ws-handoff ajax-get-or-ws-handshake-fn)
  (def receive-channel ch-recv)
  (def channel-send! send-fn)
  (def connected-uids connected-uids))

(defn gentabledata [len]
  (reduce #(conj %1 [%2 %2 0])
          []
          (map #(* % 5) (range 100 (+ 100 len)))))

;; Table data
(def tableconfig (atom {
                         :colHeaders ["MD" "TVD" "Deviation"]
                         :data        (gentabledata 15)
                         :rowHeaders  false
                         :contextMenu true}))

;; Handle messages
(defn- handle-user-ident [db-connection data]
  @(d/transact db-connection
               [{:db/id #db/id[:db.part/user]
                 :user/name (:name data)}])
                 ;:user/uid rcv-chan}])
  (let [mydb (d/db db-connection)
        rawdata (q '[:find [(pull ?e [:user/name]) ...]
                     :where [?e :user/name]]
                   mydb)
        usernames (mapv (fn [in]
                         (:user/name in)) rawdata)]
    ;(log/warn "rawdata: " usernames)
    (doseq [uid (:any @connected-uids)]
       (channel-send! uid [:user/names usernames])
       (channel-send! uid [:db/table @tableconfig])))
  (let [mydb (d/db db-connection)
        result (q '[:find [(pull ?e [:action/user :action/row :action/column :action/newval :action/instant]) ...]
                    :where [?e :action/user]]
                  mydb)]
    (doseq [uid (:any @connected-uids)]
      (channel-send! uid [:user/listactions {:result result}]))))

(defn- handle-user-set-table-value [db-connection data]
  (let [username (:username data)
        changeData (:changeData data)
        rowIdx (get changeData 0)
        colIdx (get changeData 1)
        newVal (Double/parseDouble (nth changeData 3))]
    (log/warn "user: " username " rowIdx: " rowIdx " colIdx: " colIdx " newVal: " newVal)
    ;(cond
    ;  (= 0 column) (handle-user-change-MD db-connection username changeData)
    ;  (= 1 column) (handle-user-change-TVD db-connection username changeData)
    ;  (= 2 column) (handle-user-change-Deviation db-connection username changeData)
    ;  :else (handle-user-default db-connection changeData))
    @(d/transact db-connection
                     [{:db/id #db/id[:db.part/user]
                       :action/user     username
                       :action/row      rowIdx
                       :action/column   colIdx
                       :action/newval   newVal
                       :action/instant  (now)}])
    (let [mydb (d/db db-connection)
          result (q '[:find [(pull ?e [:action/user :action/row :action/column :action/newval :action/instant]) ...]
                      :where [?e :action/user]]
                     mydb)]
      (log/warn "raw actions: " result)
      (doseq [uid (:any @connected-uids)]
        (channel-send! uid [:user/listactions {:result result}])))))

(defn- handle-user-set-history-point [db-connection idx]
  (doseq [uid (:any @connected-uids)]
    (channel-send! uid [:user/set-history-point {:idx idx}])))

(defn- ws-msg-handler [db-connection]
  (fn [{:keys [event] :as msg} _]
    (let [[id data :as ev] event]
      (case id
        :user/ident (handle-user-ident db-connection data)
        :user/set-table-value (handle-user-set-table-value db-connection data)
        :user/set-history-point (handle-user-set-history-point db-connection (:idx data))
        (log/warn "Unmatched event: " id)))))

(defn ws-message-router [db-connection]
  (sente/start-chsk-router-loop! (ws-msg-handler db-connection)
                                 receive-channel))

(defn change-monitor [change-queue]
  (log/info "starting monitor")
  ;(while true
  ;  (log/info "monitor loop"))
    ;(handle-change change-queue))
  (log/info "monitoring complete"))
