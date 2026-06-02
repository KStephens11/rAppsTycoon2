@ignore
Feature: get deployment status helper
  Scenario:
    Given url baseUrl
    And path '/api/sessions/' + sessionCode + '/basestations'
    And header X-Session-Token = token
    When method GET
    Then status 200
    * def allRapps = $response.basestations[*].deployedRapps
    * def status = 'DEPLOYING'
    * def checkStatus =
      """
      function() {
        for (var i = 0; i < allRapps.length; i++) {
          for (var j = 0; j < allRapps[i].length; j++) {
            if (allRapps[i][j].id == deploymentId) {
              return allRapps[i][j].status;
            }
          }
        }
        return 'DEPLOYING';
      }
      """
    * def status = call checkStatus
