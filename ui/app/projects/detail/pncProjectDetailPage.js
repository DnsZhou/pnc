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
  'use strict';

  angular.module('pnc.projects').component('pncProjectDetailPage', {
    bindings: {
      /**
       * object representing Project
       */
      project: '<'
    },
    templateUrl: 'projects/detail/pnc-project-detail-page.html',
    controller: ['$state', 'Project', 'paginator', '$rootScope', Controller]
  });

  function Controller($state, Project, paginator, $rootScope) {
    const $ctrl = this;

    // -- Controller API --
    
    $ctrl.cancel = cancel;
    $ctrl.update = update;

    $ctrl.buildConfigurations = {
      page: null,
      displayFields: ['name', 'project', 'buildStatus']
    };

    // --------------------

    $ctrl.$onInit = () => {
      Project.queryBuildConfigurations({ id: $ctrl.project.id }).$promise.then(function (page) { 
        $ctrl.buildConfigurations.page = paginator(page); 
      });
    };

    function reload() {
      $state.go('projects.detail', {
        projectId: $ctrl.project.id
      }, {
        reload: true
      });
    }

    $rootScope.$on('BCC_BPM_NOTIFICATION', function (event, payload) {
      if (payload.eventType === 'BCC_CREATION_SUCCESS') {
        reload();
      }
    });

    function cancel() {
      reload();
    }

    function update() {
      $ctrl.project.$update().finally(reload);
    }

  }

})();