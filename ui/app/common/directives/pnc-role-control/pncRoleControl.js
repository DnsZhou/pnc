/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2019 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function () {
  var module = angular.module('pnc.common.directives');
  module.directive('pncRoleControl', ['authService', function (authService) {
    var hasValidatedRole = function (requiredRole) {
      let userRoles = authService.getUserRole();
      return userRoles !== null && userRoles.includes(requiredRole);
    };

    return {
      restrict: 'A',

      link: function (scope, ele, attrs, ctrl) {
        let requiredRole = attrs.role;
        if (requiredRole !== null && !hasValidatedRole(requiredRole)) {
          ele.addClass('ng-hide');
        } else {
          ele.removeClass('ng-hide');
        }
      }
    };
  }]);

})();
