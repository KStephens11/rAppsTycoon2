Feature: Health Endpoints - Actuator probes

  Background:
    * url baseUrl

  Scenario: Readiness probe returns UP
    Given path '/actuator/health/readiness'
    When method GET
    Then status 200
    And match response.status == 'UP'

  Scenario: Liveness probe returns UP
    Given path '/actuator/health/liveness'
    When method GET
    Then status 200
    And match response.status == 'UP'
