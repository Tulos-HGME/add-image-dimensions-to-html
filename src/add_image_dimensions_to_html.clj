#!/usr/bin/env bb
(ns add-image-dimensions-to-html
  "A script to check that all the <img ...> tags in an HTML file have the correct width and attributes.
  Having correct width and height attributes helps with reducing cumulative layout shift (CLS) on a page,
  which is part of Google's Core Web Vitals score and may help slightly with rankings. It also improves
  the user experience on a page because elements don't jump around as the page loads.
  User must have installed on their computer:
  - Babashka
  - Git
  - ImageMagick"
  (:require [babashka.deps :as deps]
            [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.java.shell :as shell]
            [clojure.string :as str]))


(deps/add-deps '{:deps {org.babashka/spec.alpha {:git/url "https://github.com/babashka/spec.alpha"
                                                 :sha "1a841c4cc1d4f6dab7505a98ed2d532dd9d56b78"}
                        cli-matic/cli-matic {:mvn/version "0.5.4"}}})


(require '[cli-matic.core :refer [run-cmd]])


(def hgme-root (str (System/getProperty "user.home") "/Dropbox/Shared SBI/HGME HTML Tulos/"))


(def example-html-file (fs/file (fs/path hgme-root "dog-coloring-pages.html")))


(def img-tag-re #"(?s)<img.*?>")


(defn img-tags
  "Given html as a string return a list of all the image tags in a string.
  Works even when the image tag contains newlines."
  [html-string]
  (re-seq img-tag-re html-string))
(comment
  (img-tags "blah blah <img src=\"foo.jpg\"> blah <img src=\"bar.jpg\"> blah")
  ; => ("<img src=\"foo.jpg\">" "<img src=\"bar.jpg\">")
  (count (img-tags (slurp example-html-file)))
  (count (distinct (img-tags (slurp example-html-file))))
  (println (first (img-tags (slurp example-html-file))))
  (run! println (img-tags (slurp (str hgme-root "birthday-wishes.html"))))
  :_)


(defn img-src
  "Given an image tag as a string, return the value of the src attribute.
  Note the use of (?s) to allow match of newlines, because sometimes we have a newline
  before the closing \""
  [img-tag-string]
  (second (re-find #"(?s)src=\"(.*?)\"" img-tag-string)))
(comment
  (img-src (first (img-tags "blah blah <img src=\"foo.jpg\"> blah <img src=\"bar.jpg\"> blah")))
  ; => "foo.jpg"
  :_)


(defn append-attribute
  "Given image tag as a string, append the given attribute"
  [img-tag-str attr-name-str attr-value]
  (let [s (str attr-name-str "=\"" attr-value "\"")]
    (cond
      (str/ends-with? img-tag-str " />")
      (str/replace img-tag-str " />" (str " " s " />"))

      (str/ends-with? img-tag-str "/>")
      (str/replace img-tag-str "/>" (str " " s "/>"))

      (str/ends-with? img-tag-str " >")
      (str/replace img-tag-str " >" (str " " s " >"))

      :else
      (str/replace img-tag-str ">" (str " " s ">")))))
(comment
  (println (append-attribute "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\">" "width" 300))
  ;; <img src="foo.jpg" ***PINIT***="Pin me!" width="300">

  (println (append-attribute "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" >" "width" 300))
  ;; <img src="foo.jpg" ***PINIT***="Pin me!" width="300" >

  (println (append-attribute "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\"/>" "width" 300))
  ;; <img src="foo.jpg" ***PINIT***="Pin me!" width="300"/>

  (println (append-attribute "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" />" "width" 300))
  ;; <img src="foo.jpg" ***PINIT***="Pin me!" width="300" />
  :_)


(defn update-attribute
  "Given an image tag as a string, update the given attribute. Throws an error if attribute not found"
  [img-tag-str attr-name-str attr-value]
  (let [match (re-pattern (str attr-name-str "=" "\\\".+?\\\""))
        replacement (str attr-name-str "=\"" attr-value "\"")]
    (str/replace img-tag-str match replacement)))
(comment
  (update-attribute "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"300\" height=\"400\">"
                    "width" 500)
  ;; => "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"500\" height=\"400\">"
  (update-attribute "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"300\" height=\"400\">"
                    "height" 600)
  ;; => "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"300\" height=\"600\">"
  :_)


(defn has-attribute?
  "Check whether image tag has attribute"
  [img-tag-str attr-name-str]
  (let [re (re-pattern (str attr-name-str "=" "\\\".+?\\\""))]
    (some? (re-find re img-tag-str))))
(comment
  (has-attribute? "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"300\" height=\"400\">" "width") ; => true
  (has-attribute? "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" height=\"400\">" "width") ; => false
  :_)


(defn set-attribute
  "Update or append attribute on image tag"
  [img-tag-str attr-name-str attr-value]
  (if (has-attribute? img-tag-str attr-name-str)
    (update-attribute img-tag-str attr-name-str attr-value)
    (append-attribute img-tag-str attr-name-str attr-value)))
(comment
  (set-attribute "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"300\" height=\"400\">" "height" 500)
  ; => "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"300\" height=\"500\">"
  (set-attribute "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"300\">" "height" 500)
  ; => "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"300\" height=\"500\">"
  (-> "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\">"
      (set-attribute ,,, "width" 300)
      (set-attribute ,,, "height" 400))
  ; => "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"300\" height=\"400\">"
  :_)


(defn set-dimensions
  "Given an image tag as a string, set the width and height attributes, while
  making sure to preserve the capitalisation of the ***PINIT*** tag.
  img-tag        - str
  width          - int or str
  height         - int or str"
  [{:keys [img-tag width height]}]
  (-> img-tag
      (set-attribute ,,, "width" width)
      (set-attribute ,,, "height" height)))
(comment
  (set-dimensions {:img-tag  "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\">"
                   :width  300
                   :height 400})
  ;; => "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"300\" height=\"400\">"

  (set-dimensions {:img-tag  "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" >"
                   :width  300
                   :height 400})
  ;; => "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"300\" height=\"400\" >"

  ;; Check that it works when the tag closes with /> instead of >
  (set-dimensions {:img-tag  "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\"/>"
                   :width  300
                   :height 400})
  ;; => "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"300\" height=\"400\"/>"

  ;; Check that it works when the tag closes with /> with a preceding space
  (set-dimensions {:img-tag  "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" />"
                   :width  300
                   :height 400})
  ;; => "<img src=\"foo.jpg\" ***PINIT***=\"Pin me!\" width=\"300\" height=\"400\" />"
  :_)


(defn dimensions
  "Given path of image file as str, return vector [w h] of dimensions.
  Return nil if dimensions cannot be calculated (e.g. if file not found)"
  [file-path]
  (let [{:keys [exit out err]} (shell/sh "magick" "identify" "-format" "[%w %h]" file-path)]
    (if (= 1 exit)
      (do
        ;; Print warning and return nil if there's an error computing image dimensions
        (println err)
        nil)
      (edn/read-string out))))
(comment
  (dimensions (str hgme-root "this-path-does-not-exist")) ; => nil (and prints warning to stdout)
  (dimensions (str hgme-root "image-files/40th-birthday-ideas-gifts-for-women-600x800.jpg"))
  ; => [600 800]
  :_)


(defn update-img-tag
  "Given a html folder and an image tag string, return image tag str with added
  height and width attributes. The height and width are found by looking up the image on the
  local disk inside the given html folder"
  [html-folder img-tag-str]
  (let [src (img-src img-tag-str)]
    (if-not (str/starts-with? src "image-files/") ;;
      ;; Return image tag unchanged if it refers to a non-HGME image
      img-tag-str
      (if-let [[w h] (dimensions (str (fs/path html-folder src)))]
        (set-dimensions {:img-tag  img-tag-str
                         :width  w
                         :height h})
        ;; Return img-tag-str unchanged if dimensions could not be found
        img-tag-str))))
(comment
  (update-img-tag hgme-root "<img ***PINIT***=\"235 Happy Birthday Wishes &amp; Quotes\" alt=\"birthday wishes\" src=\"image-files/birthday-wishes-for-friend-its-time-to-shine-1080x720.png\"/>")
  ; => "<img ***PINIT***=\"235 Happy Birthday Wishes &amp; Quotes\" alt=\"birthday wishes\" src=\"image-files/birthday-wishes-for-friend-its-time-to-shine-1080x720.png\" width=\"1080\" height=\"721\">"

  (update-img-tag hgme-root "<img src=\"image-files/i-do-not-exist.jpg\">")
  ; => "<img src=\"image-files/i-do-not-exist.jpg\">"  (and warning printed to stdout)

  (update-img-tag hgme-root "<img src=\"images/i-am-not-in-the-right-folder.jpg\">")
  ; => "<img src=\"images/i-am-not-in-the-right-folder.jpg\">"

  (update-img-tag hgme-root "<img src=\"www.another-domain.com/image.jpg\">")
  ; => "<img src=\"www.another-domain.com/image.jpg\">"

  (update-img-tag hgme-root (last (img-tags (slurp (str hgme-root "birthday-wishes.html")))))
  :_)


(defn update-html
  "Given an html file path, slurp the file, update all image tags to include width and height attributes
  and return a string of the updated html.
  The height and width are found by looking up the dimensions of the images on the local disk"
  [html-file-path]
  (let [html-str (slurp (fs/file html-file-path))
        html-folder (fs/parent html-file-path)]
    (str/replace html-str img-tag-re #(update-img-tag html-folder %))))
(comment
  (println (update-html (str hgme-root "dog-coloring-pages.html")))
  (println (update-html (str hgme-root "birthday-wishes.html")))
  :_)


(defn update-file!
  "Given the path to an html file, overwrite it in place with a version
  where image tags are updated to include with and height attributes.
  The height and width are found by looking up the image the local disk."
  [html-file-path]
  (println "Checking" html-file-path)
  (let [html-str (slurp html-file-path)
        updated-html-str (update-html html-file-path)]
    (if (= html-str updated-html-str)
      (println "No changes made\n")
      (do
        (spit html-file-path updated-html-str)
        (println "File updated\n")))))
(comment
  (let [file-path (str hgme-root "cat-coloring-pages.html")]
    (update-file! file-path))

  (let [file-path (str hgme-root "privacy-policy.html")]
    (update-file! file-path))
  :_)


(defn update-files!
  [{:keys [_arguments]}]
  (if (seq _arguments)
    (let [files _arguments]
      (doseq [f files]
        (update-file! f)))
    (println "Error: You must specify one or more html files.\nTo see examples, run\nadd_image_dimensions_to_html.clj --help")))


(def CONFIGURATION
  {:command "add_image_dimensions_to_html.clj"
   :description "Update width and height tags in html files using actual image dimensions"
   :opts []
   :runs update-files!
   :examples ["add_image_dimensions_to_html.clj path/to/file.html"
              "add_image_dimensions_to_html.clj *.html"]})


(defn -main []
  (run-cmd *command-line-args* CONFIGURATION))


(when (= *file* (System/getProperty "babashka.file"))
  (-main))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Run comment below to copy script to /bin folder (Tobias only)
;; Make sure original script in source folder is executable before running this
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (let [clj-file-name (str (str/replace *ns* "-" "_") ".clj")
        source (fs/path (System/getProperty "user.dir") "src" clj-file-name)
        destination (fs/path (System/getProperty "user.home")
                      "Dropbox/Tobs documents/Programming/bin/" clj-file-name)]
    (println (str "Copying\n" source "\nto\n" destination))
    (fs/copy source destination {:replace-existing true, :copy-attributes true}))
  :_)