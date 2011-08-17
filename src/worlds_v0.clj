; Copyright (c) 2011, Tom Van Cutsem, Vrije Universiteit Brussel
; All rights reserved.
;
; Redistribution and use in source and binary forms, with or without
; modification, are permitted provided that the following conditions are met:
;    * Redistributions of source code must retain the above copyright
;      notice, this list of conditions and the following disclaimer.
;    * Redistributions in binary form must reproduce the above copyright
;      notice, this list of conditions and the following disclaimer in the
;      documentation and/or other materials provided with the distribution.
;    * Neither the name of the Vrije Universiteit Brussel nor the
;      names of its contributors may be used to endorse or promote products
;      derived from this software without specific prior written permission.
;
;THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
;ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
;WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
;DISCLAIMED. IN NO EVENT SHALL VRIJE UNIVERSITEIT BRUSSEL BE LIABLE FOR ANY
;DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
;(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
;LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
;ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
;(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
;SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

(ns worlds-v0
  (:use clojure.test))

;;; clj-worlds, an implementation of Alex Warth's "worlds" in Clojure
;;; Source: http://www.vpri.org/pdf/tr2011001_final_worlds.pdf

;;; Worlds support scoped side-effects: a "world-ref" or w-ref for short,
;;; always has a value that is relative to the current world.
;;; Worlds form a single-rooted hierarchy, and a world can "commit"
;;; all of its changes to its parent world.

;;; The API of clj-worlds is heavily inspired by Clojure's own "ref" API.
;;; Like refs, w-refs can be dereferenced (w-deref), set (w-ref-set) and
;;; altered (w-alter).

;;; this is clj-worlds, version 0
;;; w-refs are implemented as atoms at top-level,
;;; and as side-tables mapping refs to their values in other worlds
;;; side-tables are atoms
;;; concurrent updates to worlds are free of low-level races, but
;;; not of high-level races. In particular, commit is not an atomic operation

;; == worlds private implementation ==

(def *this-world* nil)

(defn in-world-do [world fn]
  (binding [*this-world* world] (fn)))

(def DontKnow (new Object)) ; special marker token

(defn- known? [val]
  (not (identical? val DontKnow)))

;; NOTE: this function assumes that current-world is never *this-world*.
;; Always call (deref ref) instead of (lookup-in-parent-world *this-world* ref)
(defn- lookup-in-parent-world [current-world ref]
  (if (nil? current-world)
    ;; in top-level world, latest value is stored in ref itself
    @ref
    (let [val (get @(:writes current-world) ref DontKnow)]
      (if (known? val)
          val
          (let [val (get @(:reads current-world) ref DontKnow)]
            (if (known? val)
              val
              (recur (:parent current-world) ref)))))))

;; Note: this function requires that parent-world is non-nil
;; When committing to the top-level world, call world-commit-to-top
(defn- world-commit
  [child-world parent-world]
  ;; serializability check
  (doseq [[ref val] @(:reads child-world)]
    (if ;(and (known? val)
           (not (identical? (lookup-in-parent-world parent-world ref) val))
      (throw (Exception. (str "Commit Failed, ref changed incompatibly: " ref)))))
  ;; propagate all of child-world's :writes to parent-world's :writes,
  ;; overriding any values present
  (doseq [[ref val] @(:writes child-world)]
    (swap! (:writes parent-world) assoc ref val))
  ;; propagate all of child-world's :reads to parent-world's :reads,
  ;; except for refs that have already been read from in parent-world
  (doseq [[ref val] @(:reads child-world)]
    (swap! (:reads parent-world)
      (fn [reads]
        (if (contains? reads ref)
          (assoc reads ref val)
          reads))))
  ;; clear child-world's :reads and :writes
  (reset! (:reads child-world) {})
  (reset! (:writes child-world) {}))
  
;; child-world commits to top-level
(defn- world-commit-to-top
  [child-world]
  (assert (nil? (:parent child-world)))
  ;; serializability check
  (doseq [[ref val] @(:reads child-world)]
    (if ;(and (known? val)
           (not (identical? @ref val))
      (throw (IllegalStateException.
               (str "Commit Failed, ref changed incompatibly: " ref)))))
  ;; propagate all of child-world's :writes to parent-world's :writes,
  ;; overriding any values present
  (doseq [[ref val] @(:writes child-world)]
    (reset! ref val))
  ;; propagate all of child-world's :reads to parent-world's :reads,
  ;; except for refs that have already been read from in parent-world
  ;; Not necessary when committing to top-level, which has no :reads
  
  ;; clear child-world's :reads and :writes
  (reset! (:reads child-world) {})
  (reset! (:writes child-world) {}))

;; == worlds public API ==

; top-level world can't commit, so no need for :reads
; in top-level world, each ref encapsulates its own value
(defn w-ref [val]
  (atom val))
  
(defn w-deref
  [ref]
  (if (nil? *this-world*)
    @ref ; top-level: read latest value from ref itself
    (let [val (get @(:writes *this-world*) ref DontKnow)]
      (if (known? val)
          val
          (let [val (get @(:reads *this-world*) ref DontKnow)]
            (if (known? val)
                val
                (let [val (lookup-in-parent-world (:parent *this-world*) ref)]
                  ;; if ref's :reads value does not exist or is bound to DontKnow
                  ;; in this world, mark it as read before returning it.
                  ;; This ensures the "no surprises" property,
                  ;; i.e. a ref does not appear to change spontaneously in
                  ;; *this-world* when it is updated in one of its parents.
                  (swap! (:reads *this-world*) assoc ref val)
                  val)))))))

(defn w-ref-set
  [ref val]
  (if (nil? *this-world*)
    ;; top-level: write value directly in ref itself
    (reset! ref val)
    ;; otherwise: store in current world's :writes map
    (swap! (:writes *this-world*) assoc ref val))
  val)

(defn w-alter [ref fn & args]
  (w-ref-set ref (apply fn (w-deref ref) args)))

(defn this-world [] *this-world*)

(defmacro in-world [world-expr & body]
  `(in-world-do ~world-expr (fn [] ~@body)))

(defn sprout [parent-world]
    ;; maps ref to its "old" value when it was first read in this world,
    ;; or a special DontKnow value if the ref was never read in this world
  { :reads (atom {}),
    ;; maps ref to its most recent value in this world, or special DontKnow
    ;; value if the ref was never written to in this world
    :writes (atom {}),
    :parent parent-world })

(defn commit [world]
  ;; note: a commit of the top-level world (if world is nil)
  ;; is treated as a no-op
  (if (not (nil? world))
    (if (nil? (:parent world))
      (world-commit-to-top world)
      (world-commit world (:parent world)))))
