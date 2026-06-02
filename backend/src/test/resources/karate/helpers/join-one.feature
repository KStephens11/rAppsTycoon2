@ignore
Feature: join one player helper
  Scenario:
    Given url baseUrl
    And path '/api/sessions/' + code + '/join'
    And request { displayName: '#(name)' }
    When method POST
    Then status 200
