/*
 * Copyright 2011-2020 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.core.check.substring

import scala.annotation.tailrec

import io.gatling.commons.validation._
import io.gatling.core.check._

object SubstringExtractor {
  def extractAll(prepared: String, criterion: String): List[Int] = {

    @tailrec
    def loop(fromIndex: Int, is: List[Int]): List[Int] =
      if (fromIndex >= prepared.length)
        is
      else
        prepared.indexOf(criterion, fromIndex) match {
          case -1 => is
          case i  => loop(i + criterion.length, i :: is)
        }

    loop(0, Nil)
  }
}

class SubstringFindExtractor(substring: String, occurrence: Int)
    extends FindCriterionExtractor[String, String, Int](
      "substring",
      substring,
      occurrence,
      text => {

        @tailrec
        def loop(fromIndex: Int, occ: Int): Validation[Option[Int]] =
          if (fromIndex >= substring.length)
            NoneSuccess
          else
            text.indexOf(substring, fromIndex) match {
              case -1 => NoneSuccess
              case i =>
                if (occ == occurrence)
                  Some(i).success
                else
                  loop(i + substring.length, occ + 1)
            }

        loop(0, 0)
      }
    )

class SubstringFindAllExtractor(substring: String)
    extends FindAllCriterionExtractor[String, String, Int](
      "substring",
      substring,
      SubstringExtractor.extractAll(_, substring) match {
        case Nil => NoneSuccess
        case is  => Some(is.reverse).success
      }
    )

class SubstringCountExtractor(substring: String)
    extends CountCriterionExtractor[String, String](
      "substring",
      substring,
      text => Some(SubstringExtractor.extractAll(text, substring).size).success
    )
