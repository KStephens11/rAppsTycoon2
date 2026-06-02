Feature: Tune rApp - PUT /api/sessions/{code}/rapps/{id}/tune

  Background:
    * url baseUrl
    * call read('classpath:karate/helpers/setup.feature@startGame')
    # Deploy and wait for activation (tick engine activates after 1 tick)
    Given path '/api/sessions/' + sessionCode + '/rapps/deploy'
    And header X-Session-Token = hostToken
    And request { templateId: 1, basestationId: '#(basestationId)' }
    When method POST
    Then status 201
    * def deploymentId = response.deployment.id
    # Poll until ACTIVE (max 5s)
    * def waitForActive =
      """
      function() {
        for (var i = 0; i < 10; i++) {
          java.lang.Thread.sleep(500);
          var res = karate.call(true, 'classpath:karate/helpers/get-deployment.feature',
            { sessionCode: sessionCode, deploymentId: deploymentId, token: hostToken });
          if (res.status == 'ACTIVE') return;
        }
      }
      """
    * call waitForActive

  # -----------------------------------------------------------------------
  # Happy path
  # -----------------------------------------------------------------------

  Scenario: Tune increments version and returns updated metrics
    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/tune'
    And header X-Session-Token = hostToken
    And request { configuration: { threshold: 75, aggressiveness: 'HIGH' } }
    When method PUT
    Then status 200
    And match response.deployment.version == 2
    And match response.deployment.status == 'ACTIVE'
    And match response.deployment.configuration.threshold == 75
    And match response.deployment.configuration.aggressiveness == 'HIGH'
    And match response.updatedMetrics == '#notnull'

  # -----------------------------------------------------------------------
  # Negative
  # -----------------------------------------------------------------------

  Scenario: Tune rApp owned by another player returns 403 FORBIDDEN
    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/tune'
    And header X-Session-Token = guestToken
    And request { configuration: { threshold: 75, aggressiveness: 'HIGH' } }
    When method PUT
    Then status 403
    And match response.error == 'FORBIDDEN'

  Scenario: Missing token returns 401 UNAUTHORIZED
    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/tune'
    And request { configuration: { threshold: 75, aggressiveness: 'HIGH' } }
    When method PUT
    Then status 401
    And match response.error == 'UNAUTHORIZED'
