Feature: Disable rApp - PUT /api/sessions/{code}/rapps/{id}/disable

  Background:
    * url baseUrl
    * call read('classpath:karate/helpers/setup.feature@startGame')
    Given path '/api/sessions/' + sessionCode + '/rapps/deploy'
    And header X-Session-Token = hostToken
    And request { templateId: 1, basestationId: '#(basestationId)' }
    When method POST
    Then status 201
    * def deploymentId = response.deployment.id
    # Wait for ACTIVE
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

  Scenario: Disable ACTIVE rApp returns 200 with DISABLED status
    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/disable'
    And header X-Session-Token = hostToken
    When method PUT
    Then status 200
    And match response.deployment.status == 'DISABLED'
    And match response.updatedMetrics == '#notnull'

  Scenario: Disable reverses rApp metric impact
    # Get metrics before disable
    Given path '/api/sessions/' + sessionCode + '/basestations'
    And header X-Session-Token = hostToken
    When method GET
    Then status 200
    * def metricsBefore = response.basestations[0].metrics.energyEfficiency

    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/disable'
    And header X-Session-Token = hostToken
    When method PUT
    Then status 200
    # Energy Saver boosts energyEfficiency — disabling should reduce it
    And assert response.updatedMetrics.energyEfficiency < metricsBefore

  # -----------------------------------------------------------------------
  # Negative
  # -----------------------------------------------------------------------

  Scenario: Disable already DISABLED rApp returns 409 INVALID_STATE
    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/disable'
    And header X-Session-Token = hostToken
    When method PUT
    Then status 200

    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/disable'
    And header X-Session-Token = hostToken
    When method PUT
    Then status 409
    And match response.error == 'INVALID_STATE'

  Scenario: Disable rApp owned by another player returns 403 FORBIDDEN
    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/disable'
    And header X-Session-Token = guestToken
    When method PUT
    Then status 403
    And match response.error == 'FORBIDDEN'

  Scenario: Missing token returns 401 UNAUTHORIZED
    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/disable'
    When method PUT
    Then status 401
    And match response.error == 'UNAUTHORIZED'
