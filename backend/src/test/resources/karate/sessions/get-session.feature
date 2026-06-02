Feature: Get Session State - GET /api/sessions/{code}

  Background:
    * url baseUrl
    * call read('classpath:karate/helpers/setup.feature@createSession')

  # -----------------------------------------------------------------------
  # Happy path
  # -----------------------------------------------------------------------

  Scenario: Returns session with player list
    Given path '/api/sessions/' + sessionCode
    And header X-Session-Token = hostToken
    When method GET
    Then status 200
    And match response.sessionCode == sessionCode
    And match response.state == 'LOBBY'
    And match response.players == '#[1]'
    And match response.players[0].displayName == 'HostPlayer'
    And match response.players[0].isHost == true

  # -----------------------------------------------------------------------
  # Negative
  # -----------------------------------------------------------------------

  Scenario: Non-existent code returns 404 SESSION_NOT_FOUND
    Given path '/api/sessions/XXXXXXXX'
    And header X-Session-Token = hostToken
    When method GET
    Then status 404
    And match response.error == 'SESSION_NOT_FOUND'

  Scenario: Missing token returns 401 UNAUTHORIZED
    Given path '/api/sessions/' + sessionCode
    When method GET
    Then status 401
    And match response.error == 'UNAUTHORIZED'
