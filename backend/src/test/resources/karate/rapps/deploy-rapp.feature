Feature: Deploy rApp - POST /api/sessions/{code}/rapps/deploy

  Background:
    * url baseUrl
    * call read('classpath:karate/helpers/setup.feature@startGame')

  # -----------------------------------------------------------------------
  # Happy path
  # -----------------------------------------------------------------------

  Scenario: Deploy Energy Saver returns 201 with DEPLOYING status
    Given path '/api/sessions/' + sessionCode + '/rapps/deploy'
    And header X-Session-Token = hostToken
    And request { templateId: 1, basestationId: '#(basestationId)' }
    When method POST
    Then status 201
    And match response.deployment.status == 'DEPLOYING'
    And match response.deployment.version == 1
    And match response.deployment.templateId == 1
    And match response.deployment.basestationId == basestationId
    And match response.deployment.deployedAt == '#notnull'
    And match response.deployment.id == '#number'

  Scenario: Deployment deducts cost from player money
    # Get catalogue to find template cost
    Given path '/api/rapps/catalogue'
    And header X-Session-Token = hostToken
    When method GET
    Then status 200
    * def templateCost = karate.jsonPath(response, "$.rapps[?(@.id==1)].cost")[0]

    Given path '/api/sessions/' + sessionCode + '/rapps/deploy'
    And header X-Session-Token = hostToken
    And request { templateId: 1, basestationId: '#(basestationId)' }
    When method POST
    Then status 201
    And match response.deployment.status == 'DEPLOYING'

  # -----------------------------------------------------------------------
  # Negative
  # -----------------------------------------------------------------------

  Scenario: Deploy to unowned basestation returns 403 FORBIDDEN
    Given path '/api/sessions/' + sessionCode + '/basestations'
    And header X-Session-Token = guestToken
    When method GET
    Then status 200
    * def guestBsId = response.basestations[0].id

    Given path '/api/sessions/' + sessionCode + '/rapps/deploy'
    And header X-Session-Token = hostToken
    And request { templateId: 1, basestationId: '#(guestBsId)' }
    When method POST
    Then status 403
    And match response.error == 'FORBIDDEN'

  Scenario: Invalid templateId returns 404
    Given path '/api/sessions/' + sessionCode + '/rapps/deploy'
    And header X-Session-Token = hostToken
    And request { templateId: 9999, basestationId: '#(basestationId)' }
    When method POST
    Then status 404

  Scenario: Missing templateId returns 400 VALIDATION_ERROR
    Given path '/api/sessions/' + sessionCode + '/rapps/deploy'
    And header X-Session-Token = hostToken
    And request { basestationId: '#(basestationId)' }
    When method POST
    Then status 400
    And match response.error == 'VALIDATION_ERROR'

  Scenario: Missing token returns 401 UNAUTHORIZED
    Given path '/api/sessions/' + sessionCode + '/rapps/deploy'
    And request { templateId: 1, basestationId: '#(basestationId)' }
    When method POST
    Then status 401
    And match response.error == 'UNAUTHORIZED'
