package dhall.internal

import scala.collection.generic.CanBuildFrom
import scala.collection.IterableView

private[dhall] object XCompat {
  val xCompatIsUsed = null

  implicit class MapViewSyntax[K, V, C <: Map[K, V]](private val self: IterableView[(K, V), C])
      extends AnyVal {

    def filterKeys[That](p: K => Boolean)(
        implicit bf: CanBuildFrom[IterableView[(K, V), C], (K, V), That]): That =
      self.collect[(K, V), That] { case (k, v) if p(k) => (k, v) }
  }
}
