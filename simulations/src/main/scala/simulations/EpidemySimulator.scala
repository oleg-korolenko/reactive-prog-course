package simulations

import math.random

class EpidemySimulator extends Simulator {

  def randomBelow(i: Int) = (random * i).toInt

  protected[simulations] object SimConfig {
    val population: Int = 300
    //300
    val roomRows: Int = 8
    val roomColumns: Int = 8
    val relevancePourcentage = 0.01
    val incubationPeriod = 6
    val deathFromSickPeriod = 8
    val immuneFromDeathPeriod = 2
    val healthyFromImmunePeriod = 2

    val chanceToDie = 25
    val chanceToGetInfected = 40

  }

  import SimConfig._

  val persons: List[Person] = {
    //TOD temp for tests
    val relevanceRate = (relevancePourcentage * population).toInt

    var l = List[Person]()
    for (i <- 1 to population) {
      l = new Person(i) :: l
    }
    setInfectedPrelevanceRate(l, 1, relevanceRate)
  }

  val firstAction = for (p <- persons) {
    p.scheduleMove()
  }

  def setInfectedPrelevanceRate(l: List[Person], counter: Int, limit: Int): List[Person] = {
    if (counter <= limit) {
      l.head.infected = true
      l.head.scheduleIncubate()
      l.head :: setInfectedPrelevanceRate(l.tail, counter + 1, limit)
    }
    else l
  }


  def getAllInfectedPersonsInRoom(currentPerson: Person, room: Tuple2[Int, Int]) = {
    persons.filter(p => (p.row, p.col) == room && p.infected && p.id != currentPerson.id)
  }

  // to complete: construct list of persons
  class Person(val id: Int) {
    var infected = false
    var sick = false
    var immune = false
    var dead = false

    // demonstrates random number generation
    var row: Int = randomBelow(roomRows)
    var col: Int = randomBelow(roomColumns)

    //
    // to complete with simulation logic
    //
    def scheduleMove() {
      //dead don't move !
      afterDelay(randomBelow(5) + 1) {
        if (!dead) {

          val roomsToMove = getRoomsToMove()
          roomsToMove match {
            case Vector() =>
            case _ => {
              val roomTo = roomsToMove(randomBelow(roomsToMove.size))
              row = roomTo._1
              col = roomTo._2

              //infectionLogic
              if (!immune && !infected) {
                val infectedPersons = getAllInfectedPersonsInRoom(this, (row, col))
                if (!infectedPersons.isEmpty) {
                  if (randomBelow(100) < chanceToGetInfected) {
                    infected = true
                    scheduleIncubate()
                  }
                }
              }
            }
          }

          scheduleMove()
        }

      }

    }

    def scheduleIncubate() {

      afterDelay(incubationPeriod) {
        if (!dead) {
          sick = true
          scheduleDead()
        }
      }

    }

    def scheduleDead() {
      afterDelay(deathFromSickPeriod) {
        if (!dead) {
          if (randomBelow(100) < chanceToDie) {
            dead = true
          }
          else scheduleImmune()
        }
      }
    }

    def scheduleImmune() {
      afterDelay(immuneFromDeathPeriod) {
        if (!dead) {
          immune = true
          sick = false
          scheduleHealthy()
        }
      }
    }

    def scheduleHealthy() {
      afterDelay(healthyFromImmunePeriod) {
        if (!dead) {
          immune = false
          infected = false
        }
      }
    }


    def getRoomsToMove(): Vector[(Int, Int)] = {
      val badKarmaRooms: List[(Int, Int)] = persons.filter(p => p.sick || p.dead).map(p => (p.row, p.col))
      val nextRooms = List(
        //up
        (if (row - 1 < 0) SimConfig.roomRows - 1 else row - 1, col),
        //down
        (if (row + 1 == roomRows) 0 else row + 1, col),
        //left
        (row, if (col - 1 < 0) SimConfig.roomColumns - 1 else col - 1),
        //right
        (row, if (col + 1 == SimConfig.roomColumns) 0 else col + 1)
      )

      nextRooms.filterNot(room => badKarmaRooms.contains(room)).to[Vector]
    }


  }


}
