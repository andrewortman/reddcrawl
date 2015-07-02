(function () {
    google.load('visualization', '1.0', {'packages':['corechart']});

    var reddcrawlApp = angular.module("reddcrawlApp", []);

    reddcrawlApp.controller('reddcrawlMainController', function ($scope, $interval, $timeout, $http) {
        function updateStories() {
            $http.get("/api/stories")
                .then(function (res, err) {
                    if (res.status == 200) {
                        $scope.stories = res.data;
                        $scope.updateTime = new Date();
                    } else {
                        console.log(status);
                    }
                });
        }

        updateStories();
    });

    reddcrawlApp.directive('reddcrawlStory', function($timeout, $http) {
        return {
            templateUrl: "story.html",
            restrict: "E",
            link: function(scope, element, attributes) {
                scope.showChart = false;

                scope.toggleStoryHistory = function() {
                    console.log("click " + scope.story);
                    $historyRow = $(element).find(".history-row");
                    $historyRow.toggle();

                    if(!scope.showChart) {
                        $http.get("/api/story/" + scope.story.redditShortId)
                            .then(function(res, err) {
                                console.log(res.data);
                                var data = new google.visualization.DataTable();
                                data.addColumn('number', 'Seconds since story epoch');
                                data.addColumn('number', 'Score');
                                data.addColumn('number', 'Comments');

                                res.data.history.data.forEach(function(line) {
                                    console.log(line);
                                    data.addRow([(line[0]-res.data.createdAt)/1000.0, line[1], line[3]]);
                                });


                                // Set chart options
                                var options = {
                                    width: "100%",
                                    pointSize: 2,
                                    height: 300,
                                    vAxis: {minValue: 0},
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

                                $(window).resize(function() {
                                    drawChart();
                                });
                            });
                    }
                }
            }
        }
    })
})();