(ns example.client
  "Client reference example for: Sente, DogFort, Figwheel, Om Next
   refer: Official Sente reference example: client; Peter Taoussanis (@ptaoussanis)
   refer: http://github.com/theasp/sente-nodejs-example"
  {:author "Ulf Ninow"}

  (:require
   [clojure.string  :as str]
   [taoensso.encore :as encore :refer ()]
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
   [taoensso.sente  :as sente  :refer (cb-success?)]
   [om.next :as om :refer-macros [defui]]
   [goog.dom :as gdom]
   [sablono.core :as sab :include-macros true])


  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)]
   [figwheel.client.utils :refer [enable-dev-blocks!]]))

;; (timbre/set-level! :trace) ; Uncomment for more logging
(enable-dev-blocks!)
(enable-console-print!)

(defonce app-state (atom {:msg ""}))


;;;; Util for logging output to on-screen console



(defn ->output! [fmt & args]

  (let [value (apply encore/format fmt args)]
    (do
      (swap! app-state update-in [:msg] #(str  % "\n" "• " value))
      (timbre/debug value))))

(->output! "ClojureScript appears to have loaded correctly.")

;;;; Define our Sente channel socket (chsk) client

(defonce socket-client
  (sente/make-channel-socket-client!
       "/chsk" ; Must match server Ring routing URL
       {:type   :auto
        :packer :edn}))



(let [{:keys [chsk ch-recv send-fn state]} socket-client]

  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state))   ; Watchable, read-only atom


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
  [{:as ev-msg :keys [event]}]
  (->output! "Unhandled event: %s" event))

(defmethod -event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (if (= ?data {:first-open? true})
    (->output! "Channel socket successfully established!")
    (->output! "Channel socket state change: %s" ?data)))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (->output! "Push event from server: %s" ?data))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (->output! "Handshake: %s" ?data)))

;; TODO Add your (defmethod -event-msg-handler <event-id> [ev-msg] <body>)s here...

;;;; Sente event router (our `event-msg-handler` loop)

(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-client-chsk-router!
           ch-chsk event-msg-handler)))

;;;; UI events

(defn btn1-click [ev]
  (->output! "Button 1 was clicked (won't receive any reply from server)")
  (chsk-send! [:example/button1 {:had-a-callback? "nope"}]))

(defn btn2-click [ev]
  (->output! "Button 2 was clicked (will receive reply from server)")
  (chsk-send! [:example/button2 {:had-a-callback? "indeed"}] 5000
                                     (fn [cb-reply] (->output! "Callback reply: %s" cb-reply))))



(defn btn-login-click [ev]
  (let [user-id (.-value (.getElementById js/document "input-login"))]
    (if (str/blank? user-id)
      (js/alert "Please enter a user-id first")
      (do
        (->output! "Logging in with user-id %s" user-id)

            ;;; Use any login procedure you'd like. Here we'll trigger an Ajax
            ;;; POST request that resets our server-side session. Then we ask
            ;;; our channel socket to reconnect, thereby picking up the new
            ;;; session.

        (sente/ajax-lite "/login"
                         {:method :post
                          :headers {:x-csrf-token (:csrf-token @chsk-state)}
                          :params {:user-id    (str user-id)}}
                         (fn [ajax-resp]
                           (->output! "Ajax login response: %s" ajax-resp)
                           (let [login-successful? true] ; Your logic here

                             (if-not login-successful?
                               (->output! "Login failed")
                               (do
                                 (->output! "Login successful")
                                 (sente/chsk-reconnect! chsk))))))))))


;;;; UI om next

(defui ^:once Landing
  static om/IQuery
  (query [this]  [:msg])

  Object
  (render [this]
    (let [{:keys [msg] }(om/props this)]
      (sab/html
        [:div
          [:h1 "Sente foobar examples"]
         [:p "An Ajax/WebSocket" [:strong " (random choice!)"] " has been configured for this example"]
         [:hr]
         [:p [:strong "Step 1: "] " try hitting the buttons:"]
         [:button#btn1 {:type "button" :on-click btn1-click} "chsk-send! (w/o reply)"]
         [:button#btn2 {:type "button" :on-click btn2-click} "chsk-send! (with reply)"]
         ;;
         [:p [:strong "Step 2: "] " observe std-out (for server output) and below (for client output):"]
         [:textarea#output {:style {:width "100%"  :height "200px"} :value msg}]
         ;;
         [:hr]
         [:h2 "Step 3: try login with a user-id"]
         [:p  "The server can use this id to send events to *you* specifically."]
         [:p
          [:input#input-login {:type :text :placeholder "User-id"}]
          [:button#btn-login {:type "button" :on-click btn-login-click} "Secure login!"]]]))))
         ;;


;;;; Reconciler,  read methods

(defmulti read om/dispatch)

(defmethod read :default
  [{:keys [state]} key p]
  {:value (key @state)})

(defonce rec
         (om/reconciler {:state    app-state
                         :parser (om/parser {:read read})}))

;;;; plumbing, start router only once, let always figwheel reload main()

(defn main []
  (if-let [e (gdom/getElement "app")]

    (om/add-root! rec Landing e)))

(main)

(defn start! []
  (start-router!))

(defonce _start-once (start!))

