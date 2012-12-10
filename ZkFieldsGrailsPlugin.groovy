/*
 * Copyright 2012 Rob Fletcher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import grails.plugin.zkfields.BeanPropertyAccessorFactory
import org.codehaus.groovy.grails.validation.ConstraintsEvaluator

class ZkFieldsGrailsPlugin {

	def version = '0.1.0'
	def grailsVersion = '2.1 > *'
	def dependsOn = [:]
	def pluginExcludes = []

	def title = 'ZkFields Plugin'
	def author = 'Raphael Alla'
	def authorEmail = 'raphael@mitija.com'
	def description = 'Form-field rendering based on zkui'

	def documentation = 'https://github.com/mitija/zkfields.git'
	def license = 'APACHE'
	def issueManagement = [system: 'GitHub', url: 'https://github.com/mitija/zkfields.git']
	def scm = [system: 'GitHub', url: 'https://github.com/mitija/zkfields.git']

	def doWithSpring = {
		beanPropertyAccessorFactory(BeanPropertyAccessorFactory) {
			constraintsEvaluator = ref(ConstraintsEvaluator.BEAN_NAME)
			proxyHandler = ref('proxyHandler')
		}
	}
	
}
