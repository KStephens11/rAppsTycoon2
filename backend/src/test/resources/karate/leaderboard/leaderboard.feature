Feature: Leaderboard - GET /api/sessions/{code}/leaderboard

  Background:
    * url baseUrl

  # -----------------------------------------------------------------------
  # Happy path
  # -----------------------------------------------------------------------

  Scenario: Returns leaderboard with ranked entries in LOBBY state
    * call read('classpath:karate/helpers/setup.feature@createSession')
    Given path '/api/sessions/' + sessionCode + '/leaderboard'
    And header X-Session-Token = hostToken
    When method GET
    Then status 200
    And match response.leaderboard == '#[1]'
    And match response.leaderboard[0].rank == 1
    And match response.leaderboard[0].displayName == '#string'
    And match response.leaderboard[0].compositeScore == '#number'
    And match response.leaderboard[0].scores.money == '#number'
    And match response.gameState == 'LOBBY'

  Scenario: Returns leaderboard for ACTIVE game with 2 players sorted by score
    * call read('classpath:karate/helpers/setup.feature@startGame')
    Given path '/api/sessions/' + sessionCode + '/leaderboard'
    And header X-Session-Token = hostToken
    When method GET
    Then status 200
    And match response.leaderboard == '#[2]'
    And match response.leaderboard[0].rank == 1
    And match response.leaderboard[1].rank == 2
    And match response.gameState == 'ACTIVE'

  # -----------------------------------------------------------------------
  # Negative
  # -----------------------------------------------------------------------

  Scenario: Non-existent session returns 404 SESSION_NOT_FOUND
    Given path '/api/sessions/XXXXXXXX/leaderboard'
    And header X-Session-Token = 'anytoken0000000000000000000000000000000000000000000000000000000'
    When method GET
    Then status 404
    And match response.error == 'SESSION_NOT_FOUND'

  Scenario: Missing token returns 401 UNAUTHORIZED
    * call read('classpath:karate/helpers/setup.feature@createSession')
    Given path '/api/sessions/' + sessionCode + '/leaderboard'
    When method GET
    Then status 401
    And match response.error == 'UNAUTHORIZED'
