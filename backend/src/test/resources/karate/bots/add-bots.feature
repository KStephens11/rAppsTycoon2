Feature: Add Bots - POST /api/sessions/{code}/bots

  Background:
    * url baseUrl
    * call read('classpath:karate/helpers/setup.feature@createSession')

  # -----------------------------------------------------------------------
  # Happy path
  # -----------------------------------------------------------------------

  Scenario: Host adds 2 MEDIUM bots, returns 201 with bot details
    Given path '/api/sessions/' + sessionCode + '/bots'
    And header X-Session-Token = hostToken
    And request { count: 2, difficulty: 'MEDIUM' }
    When method POST
    Then status 201
    And match response.bots == '#[2]'
    And match response.bots[0].displayName == 'Bot-Alpha'
    And match response.bots[1].displayName == 'Bot-Beta'
    And match response.bots[0].isBot == true
    And match response.bots[1].isBot == true
    And match response.bots[0].id == '#number'

  Scenario: Bot names continue from existing bots
    # Add 1 bot first
    Given path '/api/sessions/' + sessionCode + '/bots'
    And header X-Session-Token = hostToken
    And request { count: 1, difficulty: 'EASY' }
    When method POST
    Then status 201
    And match response.bots[0].displayName == 'Bot-Alpha'

    # Add another bot - should be Bot-Beta
    Given path '/api/sessions/' + sessionCode + '/bots'
    And header X-Session-Token = hostToken
    And request { count: 1, difficulty: 'HARD' }
    When method POST
    Then status 201
    And match response.bots[0].displayName == 'Bot-Beta'

  Scenario: Bots show as players in session details
    Given path '/api/sessions/' + sessionCode + '/bots'
    And header X-Session-Token = hostToken
    And request { count: 1, difficulty: 'MEDIUM' }
    When method POST
    Then status 201

    Given path '/api/sessions/' + sessionCode
    And header X-Session-Token = hostToken
    When method GET
    Then status 200
    And match response.players == '#[2]'

  Scenario: Game can start with host + bot (2 players minimum)
    Given path '/api/sessions/' + sessionCode + '/bots'
    And header X-Session-Token = hostToken
    And request { count: 1, difficulty: 'MEDIUM' }
    When method POST
    Then status 201

    Given path '/api/sessions/' + sessionCode + '/start'
    And header X-Session-Token = hostToken
    When method POST
    Then status 200
    And match response.state == 'ACTIVE'

  # -----------------------------------------------------------------------
  # Negative
  # -----------------------------------------------------------------------

  Scenario: Non-host token returns 403 FORBIDDEN
    # Add a guest first
    Given path '/api/sessions/' + sessionCode + '/join'
    And request { displayName: 'Guest' }
    When method POST
    Then status 200
    * def guestToken = response.player.sessionToken

    Given path '/api/sessions/' + sessionCode + '/bots'
    And header X-Session-Token = guestToken
    And request { count: 1, difficulty: 'EASY' }
    When method POST
    Then status 403
    And match response.error == 'FORBIDDEN'

  Scenario: Invalid token returns 401 UNAUTHORIZED
    Given path '/api/sessions/' + sessionCode + '/bots'
    And header X-Session-Token = 'invalidtoken0000000000000000000000000000000000000000000000000000'
    And request { count: 1, difficulty: 'EASY' }
    When method POST
    Then status 401
    And match response.error == 'UNAUTHORIZED'

  Scenario: Adding bots to non-existent session returns 404 SESSION_NOT_FOUND
    Given path '/api/sessions/XXXXXXXX/bots'
    And header X-Session-Token = hostToken
    And request { count: 1, difficulty: 'EASY' }
    When method POST
    Then status 404
    And match response.error == 'SESSION_NOT_FOUND'

  Scenario: Adding bots to ACTIVE session returns 409 INVALID_STATE
    # Join a guest and start the game
    Given path '/api/sessions/' + sessionCode + '/join'
    And request { displayName: 'Player2' }
    When method POST
    Then status 200

    Given path '/api/sessions/' + sessionCode + '/start'
    And header X-Session-Token = hostToken
    When method POST
    Then status 200

    Given path '/api/sessions/' + sessionCode + '/bots'
    And header X-Session-Token = hostToken
    And request { count: 1, difficulty: 'EASY' }
    When method POST
    Then status 409
    And match response.error == 'INVALID_STATE'

  Scenario: Adding bots that would exceed max players returns 409 SESSION_FULL
    # Fill slots: host (1) + 4 guests = 5 players
    * def fill =
      """
      function() {
        for (var i = 2; i <= 5; i++) {
          var result = karate.call(true, 'classpath:karate/helpers/join-one.feature', { code: sessionCode, name: 'Player' + i });
        }
      }
      """
    * call fill

    # Try to add 2 bots (would make 7, exceeds max 6)
    Given path '/api/sessions/' + sessionCode + '/bots'
    And header X-Session-Token = hostToken
    And request { count: 2, difficulty: 'EASY' }
    When method POST
    Then status 409
    And match response.error == 'SESSION_FULL'

  Scenario: Count of 0 returns 400 VALIDATION_ERROR
    Given path '/api/sessions/' + sessionCode + '/bots'
    And header X-Session-Token = hostToken
    And request { count: 0, difficulty: 'EASY' }
    When method POST
    Then status 400
    And match response.error == 'VALIDATION_ERROR'

  Scenario: Count above 5 returns 400 VALIDATION_ERROR
    Given path '/api/sessions/' + sessionCode + '/bots'
    And header X-Session-Token = hostToken
    And request { count: 6, difficulty: 'EASY' }
    When method POST
    Then status 400
    And match response.error == 'VALIDATION_ERROR'

  Scenario: Missing difficulty returns 400 VALIDATION_ERROR
    Given path '/api/sessions/' + sessionCode + '/bots'
    And header X-Session-Token = hostToken
    And request { count: 1 }
    When method POST
    Then status 400
    And match response.error == 'VALIDATION_ERROR'
