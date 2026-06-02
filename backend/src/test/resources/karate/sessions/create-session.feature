Feature: Create Game Session - POST /api/sessions

  Background:
    * url baseUrl

  # -----------------------------------------------------------------------
  # Happy path
  # -----------------------------------------------------------------------

  Scenario: Returns 201 with 8-char session code and 64-char host token
    Given path '/api/sessions'
    And request { hostName: 'TestHost' }
    When method POST
    Then status 201
    And match response.sessionCode == '#regex [A-Z0-9]{8}'
    And match response.state == 'LOBBY'
    And match response.maxPlayers == 6
    And match response.hostPlayer.sessionToken == '#regex [0-9a-f]{64}'
    And match response.hostPlayer.displayName == 'TestHost'
    And match response.createdAt == '#notnull'

  Scenario: Two separate calls produce different session codes
    Given path '/api/sessions'
    And request { hostName: 'Alice' }
    When method POST
    Then status 201
    * def code1 = response.sessionCode

    Given path '/api/sessions'
    And request { hostName: 'Bob' }
    When method POST
    Then status 201
    * def code2 = response.sessionCode

    And assert code1 != code2

  # -----------------------------------------------------------------------
  # Negative - validation errors
  # -----------------------------------------------------------------------

  Scenario: Missing hostName returns 400 VALIDATION_ERROR
    Given path '/api/sessions'
    And request {}
    When method POST
    Then status 400
    And match response.error == 'VALIDATION_ERROR'

  Scenario: Empty hostName returns 400 VALIDATION_ERROR
    Given path '/api/sessions'
    And request { hostName: '' }
    When method POST
    Then status 400
    And match response.error == 'VALIDATION_ERROR'

  Scenario: hostName longer than 50 characters returns 400 VALIDATION_ERROR
    Given path '/api/sessions'
    And request { hostName: 'ThisNameIsWayTooLongAndShouldFailValidationCheck123' }
    When method POST
    Then status 400
    And match response.error == 'VALIDATION_ERROR'
