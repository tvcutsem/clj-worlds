`clj-worlds` is an implementation of Alex Warth's "worlds" in Clojure.
The idea is explained nicely in this [paper](http://www.vpri.org/pdf/tr2011001_final_worlds.pdf).

Worlds support scoped side-effects, enabling speculative computations (e.g. "try solution A. If it fails, forget about all side-effects performed while trying A and try solution B").

In Clojure, world-sensitive state can be conveniently modelled as a special kind of [ref](http://clojure.org/refs), called a "world-ref" or w-ref for short. A w-ref always has a value that is relative to the current world. Worlds form a single-rooted hierarchy, and a world can "commit" all of its changes to its parent world.

The API of clj-worlds is heavily inspired by Clojure's own `ref` API.
Like refs, w-refs can be created (`w-ref`), dereferenced (`w-deref`),
set (`w-ref-set`) and altered (`w-alter`).

Example
=======

    (let [w (sprout (this-world)) ; w is a child of the top-level world
          r (w-ref 0)]   ; r is a world-ref
      (w-deref r)        ; in top-level world, r is 0
      (in-world w        ; in world w...
        (w-deref r)      ; ... r is also 0
        (w-ref-set r 1)) ; ... we set r to 1
      (w-deref r)        ; back in top-level world, r is still 0!
      (commit w)         ; commit all of w's changes to its parent world
      (w-deref r))       ; now, at top-level, r is 1

Comparison with refs
====================

What is the difference with Clojure's refs and STM, you ask?
Like transactions, worlds isolate and group side-effects.
Unlike transactions:

  * Worlds are first-class entities: worlds can be created, passed around, and "opened" and "closed" at will. Committing a world's changes is an explicit operation and is not tied to the control flow of a block (in contrast to STM, which operates on `dosync` blocks).

  * Worlds are not a concurrency control mechanism. While individual world operations are thread-safe, and world commits can be made atomic, worlds can see inconsistent world-lines when sibling worlds commit to their parent. There is no notion of conflict or rollback. Worlds are meant to express multiple speculative computations within a _single_ thread of control.
  
Next Steps
==========

  1. Integrate w-refs with regular refs. Make `commit` atomic. Done: see [worlds_v1.clj](https://github.com/tvcutsem/clj-worlds/blob/master/src/worlds_v1.clj).
  2. Experiment with stronger consistency checks.
     Using the default semantics of worlds, worlds can see
     inconsistent world-lines when other worlds commit to their parent.
     A possible fix is to introduce _snapshot isolation_, as for example
     employed by 
     [MVCC](http://en.wikipedia.org/wiki/Multiversion_concurrency_control)
     (which also happens to lie at the basis of Clojure's STM) or to build upon [concurrent revisions](http://research.microsoft.com/en-us/projects/revisions/).