Feature: Rollback rApp - PUT /api/sessions/{code}/rapps/{id}/rollback

  Background:
    * url baseUrl
    * call read('classpath:karate/helpers/setup.feature@startGame')
    Given path '/api/sessions/' + sessionCode + '/rapps/deploy'
    And header X-Session-Token = hostToken
    And request { templateId: 1, basestationId: '#(basestationId)' }
    When method POST
    Then status 201
    * def deploymentId = response.deployment.id
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
    # Tune to create v2
    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/tune'
    And header X-Session-Token = hostToken
    And request { configuration: { threshold: 75, aggressiveness: 'HIGH' } }
    When method PUT
    Then status 200

  # -----------------------------------------------------------------------
  # Happy path
  # -----------------------------------------------------------------------

  Scenario: Rollback from v2 returns version 1 and previous config
    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/rollback'
    And header X-Session-Token = hostToken
    When method PUT
    Then status 200
    And match response.deployment.version == 1
    And match response.deployment.status == 'ACTIVE'
    And match response.updatedMetrics == '#notnull'

  # -----------------------------------------------------------------------
  # Negative
  # -----------------------------------------------------------------------

  Scenario: Rollback at version 1 returns 409 INVALID_STATE
    # Rollback the tune first to get back to v1
    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/rollback'
    And header X-Session-Token = hostToken
    When method PUT
    Then status 200

    # Now at v1 — rollback should fail
    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/rollback'
    And header X-Session-Token = hostToken
    When method PUT
    Then status 409
    And match response.error == 'INVALID_STATE'

  Scenario: Rollback rApp owned by another player returns 403 FORBIDDEN
    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/rollback'
    And header X-Session-Token = guestToken
    When method PUT
    Then status 403
    And match response.error == 'FORBIDDEN'

  Scenario: Missing token returns 401 UNAUTHORIZED
    Given path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/rollback'
    When method PUT
    Then status 401
    And match response.error == 'UNAUTHORIZED'
