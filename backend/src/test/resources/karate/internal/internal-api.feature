Feature: Internal API - Event Generator Endpoints

  Background:
    * url baseUrl

  # -----------------------------------------------------------------------
  # GET /api/internal/sessions/active
  # -----------------------------------------------------------------------

  Scenario: Returns active sessions list with correct internal key
    * call read('classpath:karate/helpers/setup.feature@startGame')
    Given path '/api/internal/sessions/active'
    And header X-Internal-Key = internalKey
    When method GET
    Then status 200
    And match response.sessions == '#array'
    And match response.sessions[0].sessionCode == '#string'
    And match response.sessions[0].playerCount == '#number'
    And match response.sessions[0].basestationIds == '#array'

  Scenario: Missing internal key returns 401 UNAUTHORIZED
    Given path '/api/internal/sessions/active'
    When method GET
    Then status 401
    And match response.error == 'UNAUTHORIZED'

  Scenario: Wrong internal key returns 401 UNAUTHORIZED
    Given path '/api/internal/sessions/active'
    And header X-Internal-Key = 'wrong-key'
    When method GET
    Then status 401
    And match response.error == 'UNAUTHORIZED'

  # -----------------------------------------------------------------------
  # POST /api/internal/sessions/{code}/events
  # -----------------------------------------------------------------------

  Scenario: Push event to active session returns 201
    * call read('classpath:karate/helpers/setup.feature@startGame')
    Given path '/api/internal/sessions/' + sessionCode + '/events'
    And header X-Internal-Key = internalKey
    And request
      """
      {
        basestationId: '#(basestationId)',
        eventType: 'POWER_OUTAGE',
        severity: 'HIGH',
        description: 'Power failure at test basestation',
        impact: {
          health: -15.0,
          customerExperience: -10.0,
          cost: 25.0,
          energyEfficiency: -20.0,
          automationReliability: -5.0,
          slaCompliance: -12.0
        }
      }
      """
    When method POST
    Then status 201
    And match response.eventId == '#number'
    And match response.sessionCode == sessionCode
    And match response.eventType == 'POWER_OUTAGE'
    And match response.severity == 'HIGH'

  Scenario: Push event to non-existent session returns 404
    Given path '/api/internal/sessions/XXXXXXXX/events'
    And header X-Internal-Key = internalKey
    And request
      """
      {
        basestationId: 1,
        eventType: 'POWER_OUTAGE',
        severity: 'HIGH',
        description: 'Test',
        impact: { health: -10.0, customerExperience: 0.0, cost: 0.0, energyEfficiency: 0.0, automationReliability: 0.0, slaCompliance: 0.0 }
      }
      """
    When method POST
    Then status 404
    And match response.error == 'SESSION_NOT_FOUND'

  Scenario: Push event to LOBBY session returns 409 INVALID_STATE
    * call read('classpath:karate/helpers/setup.feature@createSession')
    Given path '/api/internal/sessions/' + sessionCode + '/events'
    And header X-Internal-Key = internalKey
    And request
      """
      {
        basestationId: 1,
        eventType: 'POWER_OUTAGE',
        severity: 'LOW',
        description: 'Test',
        impact: { health: -5.0, customerExperience: 0.0, cost: 0.0, energyEfficiency: 0.0, automationReliability: 0.0, slaCompliance: 0.0 }
      }
      """
    When method POST
    Then status 409
    And match response.error == 'INVALID_STATE'
