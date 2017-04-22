(ns directionalsurvey.server
  (:require [org.httpkit.server :as server]
            [ring.util.response :refer [response resource-response]]
            [clojure.tools.logging :as log]
            [ring.middleware.json :as middleware]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [directionalsurvey.system :as sys]
            [directionalsurvey.db :as mydb])
  (:gen-class))

(defroutes app-routes
  (GET "/" [] (resource-response "public/index.html"))
  (GET  "/channel" req (sys/ring-ws-handoff req))
  (POST "/channel" req (sys/ring-ws-post req))
  (POST "/graphql" req (sys/post-ws-handler req))
  (route/resources "/")
  (route/not-found "404! :("))

(defn- wrap-request-logging [handler]
  (fn [{:keys [request-method uri] :as req}]
    (let [resp (handler req)]
      (log/info (name request-method) (:status resp)
                (if-let [qs (:query-string req)]
                  (str uri "?" qs) uri))
      resp)))

(def app 
  (-> app-routes
      (handler/site)
      (wrap-request-logging)
      middleware/wrap-json-response))

(defn -main [& args]
  (log/info "creating db")
  (let [db (sys/create-db "datomic:mem://directionalsurvey")]
    (log/info "starting router")
    (sys/ws-message-router (:db-connection db))
    (log/info "starting change monitor")
    (future (sys/change-monitor (:change-queue db)))
    ;; record db and queue
    (reset! mydb/datastore db))
  (log/info "starting server")
  ;;(server/run-server app {:ip "10.6.11.46" :port 3000})
  (server/run-server app {:port 3000})
  (log/info "server started. http://localhost:3000"))
