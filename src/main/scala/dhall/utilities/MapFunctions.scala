package dhall.utilities

import dhall.internal.XCompat._

object MapFunctions {
  private val _ = xCompatIsUsed // avoid unused warning in 2.13

  implicit class RichMap[K, V](self: Map[K, V]) {
    def mapValue[X](f: V => X): Map[K, X] = self.map { case (k, v) => k -> f(v) }
    def unionWith(f: (V, V) => V, other: Map[K, V]): Map[K, V] = {
      val commonKeys = self.keySet.intersect(other.keySet)
      val combinedKeyValues = commonKeys.map(k => k -> f(self(k), other(k))).toMap
      val otherThanCommon = self.view.filterKeys(!commonKeys.contains(_)) ++ other.view.filterKeys(
        !commonKeys.contains(_))
      combinedKeyValues ++ otherThanCommon
    }
  }
}
