(ns directionalsurvey.system
  (:require [taoensso.sente :as sente]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
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

;; Handle messages
(defn post-ws-handler [request]
  (let [body (body-string request)]
    (log/info "post-ws-handler: " body)
    (response (ql/execute schema/directional-survey-schema (str body) nil nil))))

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
          tmp (q '[:find [(pull ?e [:action/user :action/row :action/column :action/newval :action/instant]) ...]
                   :where [?e :action/user]]
                 mydb)
          tmpresult (vec (sort #(compare (:action/instant %1) (:action/instant %2)) tmp))
          result (mapv (fn [in] {:user (:action/user in)
                                 :row  (:action/row in)
                                 :col  (:action/column in)
                                 :val  (:action/newval in)
                                 :inst (:action/instant in)}) tmpresult)]
      (doseq [uid (:any @connected-uids)]
        (channel-send! uid [:user/listactions {:result result}])))))

(defn- handle-user-set-history-point [db-connection idx]
  (doseq [uid (:any @connected-uids)]
    (channel-send! uid [:user/set-history-point {:idx idx}])))

;; GraphQL handlers
(defn- handle-graphql-msg-query [msg-id queryString]
  (let [rawresponse (response (ql/execute schema/directional-survey-schema queryString nil nil))
        json-response (update-in rawresponse [:body] json/generate-string nil)
        tmpresponse (json/parse-string (body-string json-response) true)
        tmpdata (:data tmpresponse)
        key (first (keys tmpdata))
        value (first (vals tmpdata))]
    (log/warn "handle-graphql-msg-query: " response)
    (log/warn "json-response: " json-response)
    (log/warn "tmpresponse: " tmpresponse)
    (log/warn "key: " key)
    (log/warn "data: " value)
    (doseq [uid (:any @connected-uids)]
      (channel-send! uid [:user/graphql {msg-id value}]))))

(defn- handle-graphql-msg-mutation-createUser []
  (doseq [uid (:any @connected-uids)]
    (channel-send! uid [:user/graphql {:setTable @mydb/tableconfig}])))

(defn- handle-graphql-msg-mutation [msg-id queryString]
  (let [rawresponse (response (ql/execute schema/directional-survey-schema queryString nil nil))
        json-response (update-in rawresponse [:body] json/generate-string nil)
        tmpresponse (json/parse-string (body-string json-response) true)
        tmpdata (:data tmpresponse)
        key (first (keys tmpdata))
        value (first (vals tmpdata))]
    (log/warn "handle-graphql-msg-mutation: " response)
    (log/warn "json-response: " json-response)
    (log/warn "tmpresponse: " tmpresponse)
    (log/warn "key: " key)
    (log/warn "data: " value)
    (doseq [uid (:any @connected-uids)]
      (channel-send! uid [:user/graphql {msg-id value}]))
    (case msg-id
      :createUser (handle-graphql-msg-mutation-createUser)
      (log/warn "handle-graphql-msg-mutation: Unknown Message!!!"))))

(defn- handle-graphql-msg [data]
  (let [msg-type (first (keys data))
        msg-id (first (vals data))
        queryString (second (vals data))]
    (log/warn "msg-type: " msg-type)
    (log/warn "msg-id: " msg-id)
    (log/warn "queryString: " queryString)
    (case msg-type
      :query (handle-graphql-msg-query msg-id queryString)
      :mutation (handle-graphql-msg-mutation msg-id queryString)
      (handle-graphql-msg-query msg-id queryString))))

(defn- ws-msg-handler [db-connection]
  (fn [{:keys [event] :as msg} _]
    (let [[id data :as ev] event]
      (case id
        :user/set-table-value (handle-user-set-table-value db-connection data)
        :user/set-history-point (handle-user-set-history-point db-connection (:idx data))
        :user/graphql (handle-graphql-msg data)
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


