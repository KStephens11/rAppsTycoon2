Feature: Start Game Session - POST /api/sessions/{code}/start

  Background:
    * url baseUrl
    * call read('classpath:karate/helpers/setup.feature@createSession')

  # -----------------------------------------------------------------------
  # Happy path
  # -----------------------------------------------------------------------

  Scenario: Host with 2 players starts game, state becomes ACTIVE
    Given path '/api/sessions/' + sessionCode + '/join'
    And request { displayName: 'Player2' }
    When method POST
    Then status 200

    Given path '/api/sessions/' + sessionCode + '/start'
    And header X-Session-Token = hostToken
    When method POST
    Then status 200
    And match response.state == 'ACTIVE'
    And match response.startedAt == '#notnull'
    And match response.players == '#[2]'

  # -----------------------------------------------------------------------
  # Negative
  # -----------------------------------------------------------------------

  Scenario: Non-host token returns 403 FORBIDDEN
    Given path '/api/sessions/' + sessionCode + '/join'
    And request { displayName: 'Player2' }
    When method POST
    Then status 200
    * def guestToken = response.player.sessionToken

    Given path '/api/sessions/' + sessionCode + '/start'
    And header X-Session-Token = guestToken
    When method POST
    Then status 403
    And match response.error == 'FORBIDDEN'

  Scenario: Invalid token returns 401 UNAUTHORIZED
    Given path '/api/sessions/' + sessionCode + '/start'
    And header X-Session-Token = 'invalidtoken0000000000000000000000000000000000000000000000000000'
    When method POST
    Then status 401
    And match response.error == 'UNAUTHORIZED'

  Scenario: Only 1 player in lobby returns 409 INVALID_STATE
    Given path '/api/sessions/' + sessionCode + '/start'
    And header X-Session-Token = hostToken
    When method POST
    Then status 409
    And match response.error == 'INVALID_STATE'

  Scenario: Already ACTIVE session returns 409 INVALID_STATE
    Given path '/api/sessions/' + sessionCode + '/join'
    And request { displayName: 'Player2' }
    When method POST
    Then status 200

    Given path '/api/sessions/' + sessionCode + '/start'
    And header X-Session-Token = hostToken
    When method POST
    Then status 200

    Given path '/api/sessions/' + sessionCode + '/start'
    And header X-Session-Token = hostToken
    When method POST
    Then status 409
    And match response.error == 'INVALID_STATE'
