(ns directionalsurvey.system
  (:require [taoensso.sente :as sente]
            [clojure.tools.logging :as log]
            [ring.util.response :refer [response resource-response]]
            [ring.util.request :refer [body-string]]
            [com.walmartlabs.lacinia :as ql]
            [directionalsurvey.schema :as schema]
            [datomic.api :as d :refer [db q]]
            [directionalsurvey.db :as mydb]))

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

;; We can watch this atom for changes if we like
(add-watch connected-uids :connected-uids
           (fn [_ _ old new]
             (when (not= old new)
               (log/warn "Connected uids change: %s" new))))

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
(defn post-ws-handler [request]
  (let [body (body-string request)]
    (log/info "post-ws-handler: " body)
    (response (ql/execute schema/star-wars-schema (str body) nil nil))))

(defn- handle-user-ident [db-connection data]
  (let [username (:name data)]
    (when (some? username)
      (do
        @(d/transact db-connection
                     [{:db/id #db/id[:db.part/user]
                       :user/name (:name data)
                       :user/password (str (rand))}])
        ;:user/uid rcv-chan}])
        (let [mydb (d/db db-connection)
              rawdata (q '[:find [(pull ?e [:user/name]) ...]
                           :where [?e :user/name]]
                         mydb)
              usernames (mapv (fn [in]
                                (:user/name in)) rawdata)]
          ;(log/warn "rawdata: " usernames)
          (doseq [uid (:any @connected-uids)]
            (channel-send! uid [:user/names usernames]))))))

  ;; Send table
  (doseq [uid (:any @connected-uids)]
     (channel-send! uid [:db/table @tableconfig]))

  ;; Send list of actions
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
    (when (some? username)
      (do
        (log/warn "user: " username " rowIdx: " rowIdx " colIdx: " colIdx " newVal: " newVal)
        @(d/transact db-connection
                     [{:db/id #db/id[:db.part/user]
                       :action/user     username
                       :action/row      rowIdx
                       :action/column   colIdx
                       :action/newval   newVal
                       :action/instant  (mydb/now)}])))

    ;; Send list of actions
    (let [mydb (d/db db-connection)
          tmpresult (q '[:find [(pull ?e [:action/user :action/row :action/column :action/newval :action/instant]) ...]
                         :where [?e :action/user]]
                       mydb)
          result (vec (sort #(compare (:action/instant %1) (:action/instant %2)) tmpresult))]
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


