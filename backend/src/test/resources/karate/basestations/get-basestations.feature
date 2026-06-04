Feature: Get Player Basestations - GET /api/sessions/{code}/basestations

  Background:
    * url baseUrl

  # -----------------------------------------------------------------------
  # Happy path
  # -----------------------------------------------------------------------

  Scenario: Returns 4 basestations per player with metrics and empty rApps
    * call read('classpath:karate/helpers/setup.feature@startGame')
    Given path '/api/sessions/' + sessionCode + '/basestations'
    And header X-Session-Token = hostToken
    When method GET
    Then status 200
    And match response.basestations == '#[4]'
    And match response.basestations[0].id == '#number'
    And match response.basestations[0].name == '#notnull'
    And match response.basestations[0].metrics.health == '#number'
    And match response.basestations[0].metrics.customerExperience == '#number'
    And match response.basestations[0].deployedRapps == '#[]'
    And match response.basestations[0].activeEvents == '#[]'

  Scenario: Each player only sees their own basestations
    * call read('classpath:karate/helpers/setup.feature@startGame')

    Given path '/api/sessions/' + sessionCode + '/basestations'
    And header X-Session-Token = hostToken
    When method GET
    Then status 200
    * def hostBsIds = $response.basestations[*].id

    Given path '/api/sessions/' + sessionCode + '/basestations'
    And header X-Session-Token = guestToken
    When method GET
    Then status 200
    * def guestBsIds = $response.basestations[*].id

    And assert hostBsIds.toString() != guestBsIds.toString()

  # -----------------------------------------------------------------------
  # Negative
  # -----------------------------------------------------------------------

  Scenario: Game not started returns 409 INVALID_STATE
    * call read('classpath:karate/helpers/setup.feature@createSession')
    Given path '/api/sessions/' + sessionCode + '/basestations'
    And header X-Session-Token = hostToken
    When method GET
    Then status 409
    And match response.error == 'INVALID_STATE'

  Scenario: Missing token returns 401 UNAUTHORIZED
    * call read('classpath:karate/helpers/setup.feature@startGame')
    Given path '/api/sessions/' + sessionCode + '/basestations'
    When method GET
    Then status 401
    And match response.error == 'UNAUTHORIZED'
