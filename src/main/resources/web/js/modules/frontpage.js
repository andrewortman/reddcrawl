(function () {
  google.load('visualization', '1.0', {'packages': ['corechart']});

  var reddcrawlApp = angular.module("reddcrawlApp", []);

  reddcrawlApp.controller('reddcrawlMainController', function ($scope, $interval, $timeout, $http) {
    $scope.updateStories = function () {
      $scope.updating = true;
      $http.get("/api/stories")
        .then(function (res, err) {
          $scope.updating = false;
          if (res.status == 200) {
            $scope.stories = res.data;
            $scope.updateTime = new Date();
          }
        });
    };

    $scope.updateStories();
  });

  reddcrawlApp.directive('reddcrawlStory', function ($timeout, $http) {
    return {
      templateUrl: "story.html",
      restrict: "E",
      link: function (scope, element, attributes) {
        scope.showChart = false;

        scope.toggleStoryHistory = function () {
          $historyRow = $(element).find(".history-row");
          $historyRow.toggle();

          if (!scope.showChart) {
            $http.get("/api/story/" + scope.story.id)
              .then(function (res, err) {
                console.log(res.data);
                var data = new google.visualization.DataTable();
                data.addColumn('number', 'Seconds since story epoch');
                data.addColumn('number', 'Score');
                data.addColumn('number', 'Comments');

                var createdAt = res.data.summary.createdAt;
                var numEntries = res.data.history.timestamp.length;
                for (var i = 0; i < numEntries; i++) {
                  data.addRow([(res.data.history.timestamp[i] - createdAt) / 1000.0, res.data.history.score[i], res.data.history.comments[i]]);
                }

                // Set chart options
                var options = {
                  width: "100%",
                  pointSize: 2,
                  height: 300,
                  vAxis: {minValue: 0},
                  hAxis: {title: "Seconds since Story Epoch", minValue: 0},
                  legend: {
                    position: 'bottom'
                  }
                };

                function drawChart() {
                  var chart = new google.visualization.AreaChart($historyRow.find(".history-chart")[0]);
                  chart.draw(data, options);
                }

                drawChart();
                // Instantiate and draw our chart, passing in some options.
                scope.showChart = true;

                $(window).resize(function () {
                  drawChart();
                });
              });
          }
        }
      }
    }
  })
})();