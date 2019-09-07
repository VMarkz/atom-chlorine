(ns chlorine.ui.console
  (:require [chlorine.ui.inline-results :as inline]
            [clojure.string :as str]
            [reagent.core :as r]
            [chlorine.aux :as aux]
            [clojure.core.async :as async :include-macros true]))

; (defonce ConsoleHTML
;   (js/eval "class Console extends HTMLElement {}
; document.registerElement('chlorine-console', {prototype: Console.prototype})"))

(defonce ^:private console-pair
  (do
    (deftype ^js ConsoleClass []
      Object
      (getTitle [_] "Chlorine REPL")
      (destroy [this]
        (-> (filter #(.. ^js % getItems (includes this))
                    (.. js/atom -workspace getPanes))
            first
            (some-> (.removeItem this)))))
    [ConsoleClass  (ConsoleClass.)]))
(def ^:private Console (first console-pair))
(def ^:private console (second console-pair))

(defn open-console [split destroy-fn]
  (let [active (. js/document -activeElement)]
    (aset console "destroy" destroy-fn)
    (.. js/atom
        -workspace
        (open "atom://chlorine-terminal" #js {:split split
                                              :searchAllPanes true
                                              :activatePane false
                                              :activateItem false})
        (then #(.focus active)))))

(defonce out-state
  (r/atom []))

(defn- cell-for [[out-type out-str] idx]
  (let [kind (out-type {:stdout :output :stderr :err :result :result})
        icon (out-type {:stdout "icon-quote" :stderr "icon-alert" :result "icon-check"})]
    [:div.cell {:key idx}
     [:div.gutter [:span {:class ["icon" icon]}]]
     [:div.content
      [:div {:class kind} out-str]]]))

(defn console-view []
  [:div.chlorine.console.native-key-bindings {:tabindex 0}
   [:<> (map cell-for @out-state (range))]])

(defonce div (. js/document createElement "div"))

(defn- chlorine-elem []
  (. div (querySelector "div.chlorine")))

(defn- all-scrolled? []
  (let [chlorine (chlorine-elem)
        chlorine-height (.-scrollHeight chlorine)
        parent-height (.. div -clientHeight)
        offset (- chlorine-height parent-height)
        scroll-pos (.-scrollTop chlorine)]
    (>= scroll-pos offset)))
(defn- scroll-to-end! [scrolled?]
  (let [chlorine (chlorine-elem)]
    (when @scrolled?
      (set! (.-scrollTop chlorine) (.-scrollHeight chlorine)))))

(defn register-console! [^js subs]
  (let [scrolled? (atom true)]
    (r/render [(with-meta console-view
                 {:component-will-update #(reset! scrolled? (all-scrolled?))
                  :component-did-update #(scroll-to-end! scrolled?)})]
              div))
  (.add subs
        (.. js/atom -workspace
            (addOpener (fn [uri] (when (= uri "atom://chlorine-terminal") console)))))
  (.add subs (.. js/atom -views (addViewProvider Console (constantly div)))))
    ; (.add subs (.. js/atom -commands
    ;                (add "chlorine-console"
    ;                     "core:copy" #(let [sel (.. js/document getSelection toString)]
    ;                                    (prn :SELECTION!)
    ;                                    (.. js/atom clipboard (write sel))))))))
(defonce registered
  (register-console! @aux/subscriptions))

(defn clear []
  (reset! out-state []))

(defn- append-text [stream text]
  (let [[old-stream old-text] (peek @out-state)]
    (if (= old-stream stream)
      (swap! out-state #(-> % pop (conj [stream (str old-text text)])))
      (swap! out-state conj [stream text]))))

(defn stdout [txt]
  (append-text :stdout txt))

(defn stderr [txt]
  (append-text :stderr txt))
