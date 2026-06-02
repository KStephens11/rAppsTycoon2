Feature: rApp Catalogue - GET /api/rapps/catalogue

  Background:
    * url baseUrl

  # -----------------------------------------------------------------------
  # Happy path
  # -----------------------------------------------------------------------

  Scenario: Returns all 7 rApps with required fields
    * call read('classpath:karate/helpers/setup.feature@createSession')
    Given path '/api/rapps/catalogue'
    And header X-Session-Token = hostToken
    When method GET
    Then status 200
    And match response.rapps == '#[7]'
    And match each response.rapps contains
      """
      {
        id: '#number',
        name: '#string',
        purpose: '#string',
        cost: '#number',
        benefit: '#string',
        risk: '#number',
        confidence: '#number',
        impact: {
          health: '#number',
          customerExperience: '#number',
          cost: '#number',
          energyEfficiency: '#number',
          automationReliability: '#number',
          slaCompliance: '#number'
        }
      }
      """

  Scenario: Energy Saver is in catalogue with correct impact values
    * call read('classpath:karate/helpers/setup.feature@createSession')
    Given path '/api/rapps/catalogue'
    And header X-Session-Token = hostToken
    When method GET
    Then status 200
    * def energySaver = karate.jsonPath(response, "$.rapps[?(@.name=='Energy Saver')]")[0]
    And match energySaver.impact.energyEfficiency == 20.0
    And match energySaver.impact.customerExperience == -5.0
    And match energySaver.impact.cost == -30.0

  # -----------------------------------------------------------------------
  # Negative
  # -----------------------------------------------------------------------

  Scenario: Missing token returns 401 UNAUTHORIZED
    Given path '/api/rapps/catalogue'
    When method GET
    Then status 401
    And match response.error == 'UNAUTHORIZED'
