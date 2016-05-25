(ns example.server
  "Client reference example for: Sente, DogFort, Figwheel, Om Next
     refer: Official Sente reference example: client; Peter Taoussanis (@ptaoussanis);
     refer: http://github.com/theasp/sente-nodejs-example"
    {:author "Ulf Ninow"}

  (:require
   [cljs.core.async    :as async  :refer (<! >! put! chan)]
   [taoensso.timbre    :as timbre :refer-macros (tracef debugf infof warnf errorf)]
   [taoensso.sente     :as sente]

   ;;; TODO: choose (uncomment) a supported web server and adapter
   ;;; You will also have to comment/uncomment the appropriate section below.
   ;; Dogfort
   [dogfort.middleware.defaults :as defaults]
   [dogfort.middleware.routes :as rts]
   [taoensso.sente.server-adapters.dogfort :refer (dogfort-adapter)]
   [dogfort.http :refer (run-http)])

  (:require-macros
   [dogfort.middleware.routes-macros :refer (defroutes GET POST)]
   [cljs.core.async.macros :as asyncm :refer (go go-loop)]))

(enable-console-print!)
;;(timbre/set-level! :trace) ; Uncomment for more logging

;;;; Ring handlers


(defn login-handler
  "Here's where you'll add your server-side login/auth procedure (Friend, etc.).
  In our simplified example we'll just always successfully authenticate the user
  with whatever user-id they provided in the auth request."
  [ring-req]
  (let [{:keys [session params] } ring-req
        {:keys [user-id]}        params]
    (debugf "Login request nrm: %s" params)
    (println "session: " session)
    (println user-id)
    {:status 200 :session (assoc session :uid user-id)}))


;; *************************************************************************
;; vvvv  UNCOMMENT FROM HERE FOR DOGFORT                                vvvv
(defonce srv (sente/make-channel-socket-server! dogfort-adapter
                                          {:packer :edn}))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
               connected-uids]} srv]


   (def ring-ajax-post                ajax-post-fn)
   (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
   (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
   (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
   (def connected-uids                connected-uids)) ; Watchable, read-only atom


(defn wrap-dir-index [handler]
  (fn [req]
    (handler
     (update-in req [:uri]
                #(if (= "/" %) "/index.html" %)))))


(defroutes ring-routes
   ;(GET  "/"      ring-req (landing-pg-handler            ring-req))
   ;redirection
           ;(GET  "/"      ring-req (fileresp ring-req))
   (GET  "/chsk"  ring-req (ring-ajax-get-or-ws-handshake ring-req))
   (POST "/chsk"  ring-req (ring-ajax-post                ring-req))
   (POST "/login" ring-req (login-handler                 ring-req)))

(def main-ring-handler
  (-> (defaults/wrap-defaults ring-routes {:wrap-file "resources/public"})
      wrap-dir-index))


(defn start-selected-web-server! [ring-handler port]
   (println "Starting dogfort...")
   (run-http ring-handler {:port port})
   {:stop-fn #(errorf "One does not simply stop dogfort...")
    :port port})

;; ^^^^  UNCOMMENT TO HERE FOR DOGFORT                                  ^^^^
;; *************************************************************************

;;;; Sente event handlers

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id) ; Dispatch on event-id


(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (debugf "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(defmethod -event-msg-handler
  :example/button1
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (debugf "event for btn1: %s " event)))




;; TODO Add your (defmethod -event-msg-handler <event-id> [ev-msg] <body>)s here...

;;;; Sente event router (our `event-msg-handler` loop)

(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-server-chsk-router!
           ch-chsk event-msg-handler)))

;;;; Some server>user async push examples

(defn start-example-broadcaster!
  "As an example of server>user async pushes, setup a loop to broadcast an
  event to all connected users every 10 seconds"
  []
  (let [broadcast!
        (fn [i]
          (debugf "Brohhtcasting server>user: %s" @connected-uids)
          ;(println "broadcastfoo")
          (doseq [uid (:any @connected-uids)]
            (chsk-send! uid
                        [:some/broadcast
                         {:what-is-this "An async broadcast pushed from server"
                          :how-often    "Every 10 seconds"
                          :to-whom      uid
                          :i            i}])))]

    (go-loop [i 0]
      (<! (async/timeout 120000))
      (broadcast! i)
      (recur (inc i)))))


(defn test-fast-server>user-pushes
  "Quickly pushes 100 events to all connected users. Note that this'll be
  fast+reliable even over Ajax!"
  []
  (doseq [uid (:any @connected-uids)]
    (doseq [i (range 10)]
      (chsk-send! uid [:fast-push/is-fast (str "hello " i "!!")]))))

(comment (test-fast-server>user-pushes))

;;;; Init stuff

(defonce    web-server_ (atom nil)) ; {:server _ :port _ :stop-fn (fn [])}
(defn  stop-web-server! [] (when-let [m @web-server_] ((:stop-fn m))))
(defn start-web-server! [& [port]]
  (stop-web-server!)
  (let [{:keys [stop-fn port] :as server-map}
        (start-selected-web-server! (var main-ring-handler) (or port 4000))
        uri (str "http://localhost:" port "/")]
    (infof "Web server is running at `%s`" uri)
    (reset! web-server_ server-map)))

(defn stop!  []  (stop-router!)  (stop-web-server!))
(defn start! [] (start-router!) (start-web-server!) (start-example-broadcaster!))
;; (defonce _start-once (start!))

(defn -main [& _]
  (start!))


(set! *main-cli-fn* -main) ;; this is required


