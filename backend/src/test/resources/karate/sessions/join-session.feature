Feature: Join Game Session - POST /api/sessions/{code}/join

  Background:
    * url baseUrl
    * call read('classpath:karate/helpers/setup.feature@createSession')

  # -----------------------------------------------------------------------
  # Happy path
  # -----------------------------------------------------------------------

  Scenario: Guest joins lobby and receives token
    Given path '/api/sessions/' + sessionCode + '/join'
    And request { displayName: 'GuestPlayer' }
    When method POST
    Then status 200
    And match response.player.displayName == 'GuestPlayer'
    And match response.player.sessionToken == '#regex [0-9a-f]{64}'
    And match response.session.sessionCode == sessionCode
    And match response.session.state == 'LOBBY'
    And match response.session.players == '#[2]'

  # -----------------------------------------------------------------------
  # Negative
  # -----------------------------------------------------------------------

  Scenario: Non-existent session code returns 404 SESSION_NOT_FOUND
    Given path '/api/sessions/XXXXXXXX/join'
    And request { displayName: 'Ghost' }
    When method POST
    Then status 404
    And match response.error == 'SESSION_NOT_FOUND'

  Scenario: Joining ACTIVE session returns 409 INVALID_STATE
    # Start the game first (need 2 players)
    Given path '/api/sessions/' + sessionCode + '/join'
    And request { displayName: 'Player2' }
    When method POST
    Then status 200

    Given path '/api/sessions/' + sessionCode + '/start'
    And header X-Session-Token = hostToken
    When method POST
    Then status 200

    Given path '/api/sessions/' + sessionCode + '/join'
    And request { displayName: 'LateArrival' }
    When method POST
    Then status 409
    And match response.error == 'INVALID_STATE'

  Scenario: Joining a full session returns 409 SESSION_FULL
    # Fill 5 more slots (host = 1, total max = 6)
    * def fill =
      """
      function() {
        for (var i = 2; i <= 6; i++) {
          var result = karate.call(true, 'classpath:karate/helpers/join-one.feature', { code: sessionCode, name: 'Player' + i });
        }
      }
      """
    * call fill

    Given path '/api/sessions/' + sessionCode + '/join'
    And request { displayName: 'SeventhPlayer' }
    When method POST
    Then status 409
    And match response.error == 'SESSION_FULL'

  Scenario: Missing displayName returns 400 VALIDATION_ERROR
    Given path '/api/sessions/' + sessionCode + '/join'
    And request {}
    When method POST
    Then status 400
    And match response.error == 'VALIDATION_ERROR'
