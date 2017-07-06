package translate

/*
 * Copyright 2015-2017 HM Revenue & Customs
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

import java.time.LocalDate

import util.{CsvReader, FileReader, KeyValueParser, WrappedPrintWriter}

object Message2Csv extends Message2Csv {}

trait Message2Csv extends KeyValueParser with FileReader with CsvReader with WrappedPrintWriter {

  val csvHeader = "Key\tEnglish\tWelsh\tComments"
  val delimiter = "\t"
  val token = "="
  val noWelshFound = "No Welsh translation found"
  val noEnglishFound = "No English messages found"
  val englishUnchanged = "English message unchanged"
  val englishChanged = "Message changed (previous message was: "
  val separator = " / "
  val englishChangedEnd = ")"
  val newLine = "\n"

  type translationLine = (String, (String, String))
  type messageLine = (String, String)


  def messages2csv(englishMessagesFileName: String, csvInputFileName: String, csvOutputFileName: String): Unit = {

    val enMap = fetchMessages(englishMessagesFileName)
    val existingTranslations = readFromCsv(csvInputFileName)


    val csvLines = enMap.map { enMessage =>

      val oExistingTranslation = existingTranslations.find(translation => enMessage._1 == translation._1)

      oExistingTranslation.fold(enMessage._1 + delimiter + enMessage._2 + delimiter + delimiter + noWelshFound) { existingTranslation =>
        checkEnglishMessageChanged(existingTranslation, enMessage)
      } + newLine
    }

    writeFile(csvOutputFileName, csvHeader + newLine + csvLines.fold("")((key, value) => key + value))
  }

  // IHT version
  def messages2csv2(receivedInputFileName: String, existingInputFileName: String, csvOutputFileName: String): Unit = {
    val receivedMap = readFromCsv(receivedInputFileName)
    val existingMap = readFromCsv(existingInputFileName)
    def outputLine(key: String, receivedEnglish: String, receivedWelsh: String, message:String) = {
      val now = LocalDate.now.toString
      key + delimiter + receivedEnglish + delimiter + receivedWelsh + delimiter + message + " " + now
    }
    val receivedLines = receivedMap.map(receivedItem => {
        val key = receivedItem._1
        val receivedEnglish = receivedItem._2._1
        val receivedWelsh = receivedItem._2._2

        val result = existingMap.find(existingItem => receivedItem._1 == existingItem._1) match {
          case None => outputLine(key, receivedEnglish, receivedWelsh, "added")
          case Some(matchedExistingItem) =>
            val matchedEnglish = matchedExistingItem._2._1
            val matchedWelsh = matchedExistingItem._2._2

            val newOutput = ChooseContent.chooseContent(receivedEnglish, receivedWelsh, matchedEnglish, matchedWelsh)
            outputLine(key, newOutput._1, newOutput._2, newOutput._3)


//            if (receivedEnglish != matchedEnglish && receivedWelsh != matchedWelsh) {
//              if (receivedWelsh.isEmpty) {
//                outputLine(key, receivedEnglish, matchedWelsh, "english changed, existing welsh kept")
//              } else {
//                outputLine(key, receivedEnglish, receivedWelsh, "english and welsh changed")
//              }
//            } else if (receivedEnglish != matchedEnglish) {
//              outputLine(key, receivedEnglish, receivedWelsh, "english changed")
//            } else if (receivedWelsh != matchedWelsh) {
//              if (receivedWelsh.isEmpty) {
//                outputLine(key, receivedEnglish, receivedWelsh, "existing welsh kept")
//              } else {
//                outputLine(key, receivedEnglish, receivedWelsh, "welsh changed")
//              }
//            } else {
//              outputLine(key, receivedEnglish, receivedWelsh, "unchanged")
//            }
        }

//        val result = (receivedItem._2._1, receivedItem._2._2, existingMap.find(existingItem => receivedItem._1 == existingItem._1)) match {
////          case (receivedEnglish, receivedWelsh, None) => outputLine(key, receivedEnglish, receivedWelsh, "added")
////          case (receivedEnglish, receivedWelsh, Some(existing))
////            if existing._2._1 != receivedEnglish && existing._2._2 != receivedWelsh && !receivedWelsh.isEmpty =>
////              outputLine(key, receivedEnglish, receivedWelsh, "english and welsh changed")
////          case (receivedEnglish, receivedWelsh, Some(existing))
////            if existing._2._1 != receivedEnglish && existing._2._2 != receivedWelsh && receivedWelsh.isEmpty =>
////              outputLine(key, receivedEnglish, existing._2._2, "english changed, existing welsh kept")
////          case (receivedEnglish, receivedWelsh, Some(existing))
////            if existing._2._1 != receivedEnglish =>
////              outputLine(key, receivedEnglish, receivedWelsh, "english changed")
//          case (receivedEnglish, receivedWelsh, Some(existing)) if existing._2._2 != receivedWelsh && !receivedWelsh.isEmpty =>
//              outputLine(key, receivedEnglish, receivedWelsh, "welsh changed")
//          case (receivedEnglish, receivedWelsh, Some(existing)) if existing._2._2 != receivedWelsh && receivedWelsh.isEmpty =>
//            outputLine(key, receivedEnglish, receivedWelsh, "existing welsh kept")
//          case (receivedEnglish, receivedWelsh, Some(_)) =>
//              outputLine(key, receivedEnglish, receivedWelsh, "unchanged")
//        }
        result + newLine
      }
    )
    val unaffectedItems: Map[String, (String, String)] = existingMap.filter(existingItem => !receivedMap.exists(receivedItem => receivedItem._1 == existingItem._1))
    val existingLinesUnaffected = unaffectedItems.map( xx => outputLine( xx._1, xx._2._1, xx._2._2, "unchanged" )+ newLine)

    val ee = receivedLines.fold("")((key, value) => key + value) +
      existingLinesUnaffected.fold("")((key, value) => key + value)

    writeFile(csvOutputFileName, ee)
  }

  private def checkEnglishMessageChanged(translation: translationLine, enMessage: messageLine): String = {
    if (translation._2._1 == enMessage._2) {
      if (translation._2._2 == "") {
        translation._1 + delimiter + enMessage._2 + delimiter + delimiter + noWelshFound
      }
      else {
        translation._1 + delimiter + translation._2._1 + delimiter + translation._2._2 + delimiter + englishUnchanged
      }
    }
    else {
      translation._1 + "\t" + enMessage._2 + delimiter + delimiter + englishChanged + translation._2._1 + separator + translation._2._2 + englishChangedEnd
    }
  }


  def fetchMessages(lang: String): Map[String, String] = {
    val lines = for (line <- linesFromFile(lang)) yield line
    lines.flatMap { line =>
      splitKeyValues(line, token).map(line => line._1 -> line._2._1)
    }.toMap
  }
}