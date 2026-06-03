@ignore
Feature: Tuning a rApp helper
  Scenario:
    Given url baseUrl
    And path '/api/sessions/' + sessionCode + '/rapps/' + deploymentId + '/tune'
    And header X-Session-Token = token
    And request { threshold: 75, aggressiveness: 'HIGH' }
    When method PUT
    * def status = responseStatus
