(ns getnf
  (:require ["fs" :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [nbb.core :refer [await]]
            [promesa.core :as p]
            [reagent.core :as r]
            ["ink" :refer
             [render useInput useStdout useApp Box
              Text]]
            ["@inkjs/ui" :refer [MultiSelect]]
            ["env-paths$default" :as envp]))

(def nerd-fonts-repo
  "https://api.github.com/repos/ryanoasis/nerd-fonts/")
(def nerd-fonts-release "releases/latest")
(def nerd-fonts-contents
  "contents/patched-fonts?ref=master")

(defn get-env-path
  [name env]
  (-> (envp name #js {:suffix ""})
      (js->clj :keywordize-keys true)
      env))

(def getnf-data-dir (get-env-path "getnf" :data))
(def getnf-cache-dir
  (get-env-path "getnf" :cache))
(def system-fonts-dir
  (get-env-path "fonts" :data))

(defn nerd-fonts-get-data
  [path]
  (p/-> (js/fetch (str nerd-fonts-repo path))
        .json
        (js->clj :keywordize-keys true)))

(defn get-nf-version
  []
  (p/->> (nerd-fonts-get-data nerd-fonts-release)
         :name))

(defn write-font-version!
  [version]
  (when-not (fs/existsSync getnf-data-dir)
    (fs/mkdirSync getnf-data-dir
                  #js {:recursive true}))
  (fs/writeFileSync (str getnf-data-dir
                         "/version")
                    version))

(defn version-file-exists?
  []
  (fs/existsSync (str getnf-data-dir "/version")))

(defn read-font-version!
  []
  (try (let [content (fs/readFileSync
                      (str getnf-data-dir
                           "/version"))]
         (str/trim (str content)))
       (catch js/Error _ nil)))

(defn version-changed?
  [version]
  (not (= version (read-font-version!))))

(defn get-font-names
  []
  (p/->> (nerd-fonts-get-data nerd-fonts-contents)
         (mapv :name)))

(defn save-font-names!
  [font-names update?]
  (let [data (str font-names)
        file (str getnf-data-dir
                  "/nerd-fonts.edn")]
    (if-not (fs/existsSync file)
      (fs/writeFileSync file data)
      (when update?
        (fs/writeFileSync file data)))))

(await (p/let [font-names (get-font-names)
               version (get-nf-version)]
         (when-not (version-file-exists?)
           (write-font-version! version))
         (let [update? (version-changed? version)]
           (save-font-names! font-names update?)
           (when update?
             (write-font-version! version)))))

(defn read-font-names!
  []
  (try (let [content (fs/readFileSync
                      (str getnf-data-dir
                           "/nerd-fonts.edn"))]
         (edn/read-string (str content)))
       (catch js/Error _ nil)))

(def multi-select-options
  (->> (read-font-names!)
       (mapv (fn [x] {:label x, :value x}))))

(def values (r/atom []))

(defn Header
  []
  [:> Box
   {:justifyContent "center",
    :borderStyle "double",
    :width "100%"} [:> Text "Welcome to getNF"]])

(defn Multi-select
  [rows]
  [:> Box {:flexDirection "column", :gap 1}
   [:> MultiSelect
    {:options multi-select-options,
     ;; add an on change and on submit
     ;; options
     :onChange #(reset! values %),
     :visibleOptionCount rows}]])

(defn Selected-options
  []
  [:> Box {:borderStyle "single", :width "100%"}
   [:> Text
    (str "Selected: " (str/join ", " @values))]])

(defn Window
  []
  (let [fns (useApp)
        exit (.-exit fns)
        stnd (useStdout)
        stdout (.-stdout stnd)
        height (.-rows stdout)
        width (.-columns stdout)]
    (useInput (fn [input key]
                (cond (or (= "q" input)
                          (.-escape key))
                      (exit))))
    [:> Box
     {:marginX 1,
      :marginY 1,
      :height (- height 1),
      :width (- width 5),
      :alignItems "flex-start",
      :justifyContent "flex-start",
      :flexDirection "column",
      :borderStyle "single"} [Header]
     [Multi-select (- height 8)]
     [Selected-options]]))

(def enterAltScreenCommand "\u001b[?1049h")

(def leaveAltScreenCommand "\u001b[?1049l")

(.write (.-stdout js/process)
        enterAltScreenCommand)

(.on js/process
     "exit"
     (fn []
       (.write (.-stdout js/process)
               leaveAltScreenCommand)))

(render (r/as-element [:f> Window]))
