/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.tail

import cats.laws._
import cats.laws.discipline._
import cats.effect.Sync
import monix.eval.Coeval
import monix.execution.exceptions.DummyException
import scala.util.{Failure, Success}

object IterantFoldRightSuite extends BaseTestSuite {
  def exists(ref: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    ref.foldRightL(Coeval(false)) { (e, next) =>
      if (p(e)) Coeval(true) else next
    }

  def forall(ref: Iterant[Coeval, Int], p: Int => Boolean): Coeval[Boolean] =
    ref.foldRightL(Coeval(true)) { (e, next) =>
      if (!p(e)) Coeval(false) else next
    }

  def concat[F[_], A](lh: Iterant[F, A], rh: Iterant[F, A])
    (implicit F: Sync[F]): Iterant[F, A] = {

    Iterant.suspend[F, A] {
      lh.foldRightL(F.pure(rh)) { (a, rest) =>
        F.pure(Iterant.nextS(a, rest))
      }
    }
  }

  test("foldRightL can express existsL") { implicit s =>
    check2 { (stream: Iterant[Coeval, Int], p: Int => Boolean) =>
      exists(stream, p) <-> stream.existsL(p)
    }
  }

  test("foldRightL can express forallL") { implicit s =>
    check2 { (stream: Iterant[Coeval, Int], p: Int => Boolean) =>
      forall(stream, p) <-> stream.forallL(p)
    }
  }

  test("foldRightL can express ++") { implicit s =>
    check2 { (lh: Iterant[Coeval, Int], rh: Iterant[Coeval, Int]) =>
      concat(lh, rh) <-> lh ++ rh
    }
  }

  test("foldRightL can short-circuit for exists") { implicit s =>
    var effect = 0
    val ref = Iterant[Coeval].of(1, 2, 3, 4) ++ Iterant[Coeval].suspend(Coeval {
      effect += 1
      Iterant[Coeval].empty[Int]
    })

    val r1 = exists(ref, _ == 6)
    assertEquals(r1.runTry(), Success(false))
    assertEquals(effect, 1)

    val r2 = exists(ref, _ == 3)
    assertEquals(r2.runTry(), Success(true))
    assertEquals(effect, 1)
  }

  test("foldRightL protects against broken op") { implicit s =>
    var effect = 0
    val dummy = DummyException("dummy")

    val ref = Iterant[Coeval].of(1, 2, 3)
      .guarantee(Coeval { effect += 1 })
      .foldRightL(Coeval(0))((_, _) => throw dummy)

    assertEquals(effect, 0)
    assertEquals(ref.runTry(), Failure(dummy))
    assertEquals(effect, 1)
  }

  test("foldRightL protects against broken cursors") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val ref = Iterant[Coeval].nextCursorS(ThrowExceptionCursor[Int](dummy), Coeval(Iterant[Coeval].empty[Int]))
      .guarantee(Coeval { effect += 1 })
      .foldRightL(Coeval(0))((a, acc) => acc.map(_ + a))

    assertEquals(effect, 0)
    assertEquals(ref.runTry(), Failure(dummy))
    assertEquals(effect, 1)
  }

  test("foldRightL protects against broken batches") { implicit s =>
    val dummy = DummyException("dummy")
    var effect = 0

    val ref = Iterant[Coeval].nextBatchS(ThrowExceptionBatch[Int](dummy), Coeval(Iterant[Coeval].empty[Int]))
      .guarantee(Coeval { effect += 1 })
      .foldRightL(Coeval(0))((a, acc) => acc.map(_ + a))

    assertEquals(effect, 0)
    assertEquals(ref.runTry(), Failure(dummy))
    assertEquals(effect, 1)
  }
}
