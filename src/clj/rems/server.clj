(ns rems.server
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [luminus.http-server :as http]
            [luminus.repl-server :as repl]
            [mount.core :as mount]
            [rems.application.search :as search]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.handler :as handler]
            [rems.validate :as validate])
  (:import [org.eclipse.jetty.server.handler.gzip GzipHandler])
  (:refer-clojure :exclude [parse-opts]))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :parse-fn #(Integer/parseInt %)]])

(defn- jetty-configurator [server]
  (let [pool (.getThreadPool server)]
    (.setName pool "jetty-handlers")
    (.setHandler server
                 (doto (GzipHandler.)
                   (.setIncludedMimeTypes (into-array ["text/css"
                                                       "text/plain"
                                                       "text/javascript"
                                                       "application/javascript"
                                                       "application/json"
                                                       "application/transit+json"
                                                       "image/x-icon"
                                                       "image/svg+xml"]))
                   (.setMinGzipSize 1024)
                   (.setHandler (.getHandler server))))

    server))

(mount/defstate
  ^{:on-reload :noop}
  http-server
  :start
  (http/start (merge {:handler handler/handler
                      :send-server-version? false
                      :port (:port env)
                      :configurator jetty-configurator}
                     (when-not (:port env)
                       {:http? false})
                     (when (:ssl-port env)
                       {:ssl? true
                        :ssl-port (:ssl-port env)
                        :keystore (:ssl-keystore env)
                        :key-password (:ssl-keystore-password env)})
                     (:jetty-extra-params env)))
  :stop
  (when http-server (http/stop http-server)))

(mount/defstate
  ^{:on-reload :noop}
  repl-server
  :start
  (when-let [nrepl-port (env :nrepl-port)]
    (repl/start {:port nrepl-port}))
  :stop
  (when repl-server
    (repl/stop repl-server)))

(defn- refresh-caches []
  (log/info "Refreshing caches")
  (applications/refresh-all-applications-cache!)
  (search/refresh!)
  (log/info "Caches refreshed"))

(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped")))

(defn start-app [& args]
  (doseq [component (-> args
                        (parse-opts cli-options)
                        mount/start-with-args
                        :started)]
    (log/info component "started"))
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app))
  (validate/validate)
  (refresh-caches))
