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

(ns test.worlds
  (:use clojure.test))
  
;(use 'worlds-v0)
(use 'worlds-v1)

(deftest test-scoped-side-effects
  (let [w (sprout (this-world))
        r (w-ref 0)]
    (is (= 0 (w-deref r)))
    (is (= (this-world) (:parent w)))
    (in-world w
      (is (= 0 (w-deref r)))
      (is (= 1 (w-ref-set r 1)))
      (is (= 1 (w-deref r))))
    (is (= 0 (w-deref r)))
    (in-world w
      (is (= 1 (w-deref r)))
      (is (= 2 (w-alter r inc))))
    (is (= 0 (w-deref r)))))

(deftest test-top-level-commit
  (is (= nil (commit (this-world))))) ; no-op
    
(deftest test-commit
  (let [parent (sprout (this-world))
        child (sprout parent)
        r (w-ref 0)]
    (is (= parent (:parent child)))
    (in-world child
      (is (= 1 (w-ref-set r 1)))
      (is (= 1 (w-deref r))))
    (in-world parent
      (is (= 0 (w-deref r))))
    (in-world (this-world)
      (is (= 0 (w-deref r))))
    (commit child)
    (in-world parent
      (is (= 1 (w-deref r))))
    (in-world (this-world)
      (is (= 0 (w-deref r))))
    (in-world child
      (is (= 1 (w-deref r))))))
      
(deftest test-commit-read-only
  (let [parent (sprout (this-world))
        child (sprout parent)
        r (w-ref 0)]
    (is (= parent (:parent child)))
    (in-world child
      (is (= 0 (w-deref r))))
    (in-world parent
      (is (= 0 (w-deref r))))
    (in-world (this-world)
      (is (= 0 (w-deref r))))
    (commit child)
    (in-world parent
      (is (= 0 (w-deref r))))
    (in-world (this-world)
      (is (= 0 (w-deref r))))
    (in-world child
      (is (= 0 (w-deref r))))))
      
(deftest test-top-level-rw
  (let [r (w-ref 0)]
    (is (= 0 (w-deref r)))
    (is (= 1 (w-ref-set r 1)))
    (is (= 1 (w-deref r)))))
    
(deftest test-top-level-commit
  (let [r (w-ref 0)]
    (is (= 1 (w-alter r inc)))
    (commit (this-world))
    (is (= 1 (w-deref r)))))

(deftest commit-to-top-level
  (let [w (sprout (this-world))
        r (w-ref 0)]
    (is (= 0 (w-deref r)))
    (in-world w
      (is (= 1 (w-alter r inc)))
      (commit w))
    (is (= 1 (w-deref r)))))

(deftest test-serializability-check-failed
  (let [w (sprout (this-world))
        r (w-ref 0)]
    (in-world w
      (is (= 1 (w-alter r inc))))
    (is (= 2 (w-ref-set r 2)))
    (is (thrown-with-msg? IllegalStateException #"Commit Failed" (commit w)))))

(deftest test-no-surprises
  (let [w (sprout (this-world))
        r (w-ref 0)]
    (is (= 1 (w-alter r inc)))
    (in-world w
      (is (= 1 (w-deref r))))
    (is (= 2 (w-alter r inc)))
    (in-world w
      ;; in w, r still derefs as 1, the above w-alter does not affect it
      (is (= 1 (w-deref r))))))

(run-tests)