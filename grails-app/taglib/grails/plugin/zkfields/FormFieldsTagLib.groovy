/*
 * Copyright 2012 Rob Fletcher
 * Copyright 2012 Raphaël Alla (port of the fields plugin to zk)
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

package grails.plugin.zkfields

import groovy.xml.MarkupBuilder
import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.grails.plugins.support.aware.GrailsApplicationAware
import org.codehaus.groovy.grails.scaffolding.DomainClassPropertyComparator
import org.codehaus.groovy.grails.web.pages.GroovyPage
import static FormFieldsTemplateService.toPropertyNameFormat
import org.codehaus.groovy.grails.commons.*
import static org.codehaus.groovy.grails.commons.GrailsClassUtils.getStaticPropertyValue

import org.zkoss.zk.ui.event.*
import org.zkoss.zul.*

class FormFieldsTagLib implements GrailsApplicationAware {

    static final namespace = 'zkf'
    static final String BEAN_PAGE_SCOPE_VARIABLE = 'f:with:bean'
    static final String PREFIX_PAGE_SCOPE_VARIABLE = 'f:with:prefix'
    static final String VIEWMODE_PAGE_SCOPE_VARIABLE = 'f:with:viewmode'

    FormFieldsTemplateService formFieldsTemplateService
    GrailsApplication grailsApplication
    BeanPropertyAccessorFactory beanPropertyAccessorFactory

    def with = { attrs, body ->
        if (!attrs.bean) throwTagError("Tag [with] is missing required attribute [bean]")
        def bean = resolveBean(attrs.bean)
        def prefix = resolvePrefix(attrs.prefix)
        def viewmode = resolvePrefix(attrs.viewmode)
        try {
            pageScope.variables[BEAN_PAGE_SCOPE_VARIABLE] = bean
            pageScope.variables[PREFIX_PAGE_SCOPE_VARIABLE] = prefix
            pageScope.variables[VIEWMODE_PAGE_SCOPE_VARIABLE] = viewmode
            def start = System.currentTimeMillis()
            out << body()
            //println "rendering form for " + bean + " in " + (System.currentTimeMillis() - start).toString()
        } finally {
            pageScope.variables.remove(BEAN_PAGE_SCOPE_VARIABLE)
            pageScope.variables.remove(PREFIX_PAGE_SCOPE_VARIABLE)
            pageScope.variables.remove(VIEWMODE_PAGE_SCOPE_VARIABLE)
        }
    }

    def all = { attrs ->
        if (!attrs.bean) throwTagError("Tag [all] is missing required attribute [bean]")
        def bean = resolveBean(attrs.bean)
        def prefix = resolvePrefix(attrs.prefix)
        def domainClass = resolveDomainClass(bean)
        if (domainClass) {
            for (property in resolvePersistentProperties(domainClass, attrs)) {
                out << field(bean: bean, property: property.name, prefix:prefix)
            }
        } else {
            throwTagError('Tag [all] currently only supports domain types')
        }
    }

    def field = { attrs, body ->
        if (attrs.containsKey('bean') && !attrs.bean) throwTagError("Tag [field] requires a non-null value for attribute [bean]")
        if (!attrs.property) throwTagError("Tag [field] is missing required attribute [property]")

        def bean = attrs.remove('bean')
        def property = attrs.remove('property')

        def propertyAccessor = resolveProperty(bean, property)
        if (propertyAccessor.persistentProperty?.embedded) {
            renderEmbeddedProperties(bean, propertyAccessor, attrs)
        } else {
            def model = buildModel(propertyAccessor, attrs)
            def fieldAttrs = [:]
            def inputAttrs = [:]
            
            attrs.each { k, v ->
                if (k?.startsWith("input-"))
                    inputAttrs[k.replace("input-", '')] = v
                else
                    fieldAttrs[k] = v
            }

            renderLabel(model)
            if (hasBody(body)) {
                model.widget = body(model+inputAttrs)
            } else {
                //model.widget = renderWidget(propertyAccessor, model, inputAttrs)
                renderWidget(propertyAccessor, model, inputAttrs)
            }

            /*def template = formFieldsTemplateService.findTemplate(propertyAccessor, 'field')
            if (template) {
                out << render(template: template.path, plugin: template.plugin, model: model+fieldAttrs)
            } else {
                out << renderLabel(model)
                out << renderDefaultField(model)
            }*/
        }
    }

    private void renderLabel(model) {
        z.label([value: model.label])
    }

    private void renderEmbeddedProperties(bean, BeanPropertyAccessor propertyAccessor, attrs) {
        def legend = resolveMessage(propertyAccessor.labelKeys, propertyAccessor.defaultLabel)
        out << applyLayout(name: '_fields/embedded', params: [type: toPropertyNameFormat(propertyAccessor.propertyType), legend: legend]) {
            for (embeddedProp in resolvePersistentProperties(propertyAccessor.persistentProperty.component, attrs)) {
                def propertyPath = "${propertyAccessor.pathFromRoot}.${embeddedProp.name}"
                out << field(bean: bean, property: propertyPath, prefix:attrs.prefix)
            }
        }
    }

    def input = { attrs ->
        if (!attrs.bean) throwTagError("Tag [$name] is missing required attribute [bean]")
        if (!attrs.property) throwTagError("Tag [$name] is missing required attribute [property]")

        def bean = attrs.remove('bean')
        def property = attrs.remove('property')

        def propertyAccessor = resolveProperty(bean, property)
        def model = buildModel(propertyAccessor, attrs)

        out << renderWidget(propertyAccessor, model, attrs)
    }

    private BeanPropertyAccessor resolveProperty(beanAttribute, String propertyPath) {
        def bean = resolveBean(beanAttribute)
        beanPropertyAccessorFactory.accessorFor(bean, propertyPath)
    }

    private Map buildModel(BeanPropertyAccessor propertyAccessor, Map attrs) {
        def value = attrs.containsKey('value') ? attrs.remove('value') : propertyAccessor.value
        def widget= attrs.containsKey('widget')? attrs.remove('widget'):''
        def valueDefault = attrs.remove('default')
        [
                bean: propertyAccessor.rootBean,
                property: propertyAccessor.pathFromRoot,
                type: propertyAccessor.propertyType,
                beanClass: propertyAccessor.beanClass,
                label: resolveLabelText(propertyAccessor, attrs),
                value: (value instanceof Number || value) ? value : valueDefault,
                constraints: propertyAccessor.constraints,
                persistentProperty: propertyAccessor.persistentProperty,
                errors: propertyAccessor.errors.collect { message(error: it) },
                required: attrs.containsKey("required") ? Boolean.valueOf(attrs.remove('required')) : propertyAccessor.required,
                invalid: attrs.containsKey("invalid") ? Boolean.valueOf(attrs.remove('invalid')) : propertyAccessor.invalid,
                viewmode: pageScope.variables[VIEWMODE_PAGE_SCOPE_VARIABLE],
                prefix: resolvePrefix(attrs.remove('prefix')),
                widget: widget,
        ]
    }

    private String renderWidget(BeanPropertyAccessor propertyAccessor, Map model, Map attrs = [:]) {
        // Disabling template search which is taking *FAR* too long
        /*def template = formFieldsTemplateService.findTemplate(propertyAccessor, 'input')
        if (template) {
            render template: template.path, plugin: template.plugin, model: model + attrs
        } else { */
            renderDefaultInput model, attrs
        //}
    }

    private Object resolveBean(beanAttribute) {
        def bean = pageScope.variables[BEAN_PAGE_SCOPE_VARIABLE]
        if (!bean) {
            // Tomcat throws NPE if you query pageScope for null/empty values
            if (beanAttribute.toString()) {
                bean = pageScope.variables[beanAttribute]
            }
        }
        if (!bean) bean = beanAttribute
        bean
    }
    
    private String resolvePrefix(prefixAttribute) {
        def prefix = pageScope.variables[PREFIX_PAGE_SCOPE_VARIABLE]
        // Tomcat throws NPE if you query pageScope for null/empty values
        if (prefixAttribute?.toString()) {
            prefix = pageScope.variables[prefixAttribute]
        }
        if (!prefix) prefix = prefixAttribute
        if (prefix && !prefix.endsWith('.'))
            prefix = prefix+'.'
        prefix ?: ''
    }

    private GrailsDomainClass resolveDomainClass(bean) {
        resolveDomainClass(bean.getClass())
    }

    private GrailsDomainClass resolveDomainClass(Class beanClass) {
        grailsApplication.getDomainClass(beanClass.name)
    }

    private List<GrailsDomainClassProperty> resolvePersistentProperties(GrailsDomainClass domainClass, attrs) {
        def properties = domainClass.persistentProperties as List

        def blacklist = attrs.except?.tokenize(',')*.trim() ?: []
        blacklist << 'dateCreated' << 'lastUpdated'
        def scaffoldProp = getStaticPropertyValue(domainClass.clazz, 'scaffold')
        if (scaffoldProp) {
            blacklist.addAll(scaffoldProp.exclude)
        }
        properties.removeAll { it.name in blacklist }
        properties.removeAll { !it.domainClass.constrainedProperties[it.name].display }

        Collections.sort(properties, new DomainClassPropertyComparator(domainClass))
        properties
    }

    private boolean hasBody(Closure body) {
        return !body.is(GroovyPage.EMPTY_BODY_CLOSURE)
    }

    private String resolveLabelText(BeanPropertyAccessor propertyAccessor, Map attrs) {
        def labelText
        def label = attrs.remove('label')
        if (label) {
            labelText = message(code: label, default: label)
        }
        if (!labelText && propertyAccessor.labelKeys) {
            labelText = resolveMessage(propertyAccessor.labelKeys, propertyAccessor.defaultLabel)
        }
        if (!labelText) {
            labelText = propertyAccessor.defaultLabel
        }
        labelText
    }
    
    private String resolveMessage(List<String> keysInPreferenceOrder, String defaultMessage) {
        def message = keysInPreferenceOrder.findResult { key ->
            message code: key, default: null
        }
        message ?: defaultMessage
    }

    private String renderDefaultField(Map model) {
      //  out << model.label //  z.label([value: model.label]).toString()
        model.widget
    /*  def classes = ['fieldcontain']
        if (model.invalid) classes << 'error'
        if (model.required) classes << 'required'

        def writer = new StringWriter()
        new MarkupBuilder(writer).div(class: classes.join(' ')) {
            label(for: (model.prefix?:'')+model.property, model.label) {
                if (model.required) {
                    span(class: 'required-indicator', '*')
                }
            } 
            mkp.yieldUnescaped model.widget
        }
        writer.toString() */
    }

    private String renderDefaultInput(Map model, Map attrs = [:]) {
        attrs.name = (model.prefix?:'')+model.property
        attrs.id =  attrs.id ?: ((model.prefix ?: '') + model.property)
        attrs.value = model.value
        if (model.required) attrs.constraint = "no empty" // TODO: configurable how this gets output? Some people prefer required="required"
        if (model.invalid) attrs.invalid = ""
        if (!model.constraints.editable) attrs.readonly = ""
        if (model.type in [String, null]) {
            return renderStringInput(model, attrs)
        } else if (model.type in [boolean, Boolean]) {
            attrs.checked  = model.value
            attrs.value = null
            if (model.viewmode == 'readonly.') {
                attrs.disabled="True"
                z.checkbox(attrs)
                return
            }
            return z.checkbox(attrs)
        } else if (model.type.isPrimitive() || model.type in Number) {
            return renderNumericInput(model, attrs)
        } else if (model.type in URL) {
            return g.field(attrs + [type: "url"])
        } else if (model.type.isEnum()) {
            if (attrs.value instanceof Enum)
                attrs.value = attrs.value.name()
            attrs.keys = model.type.values()*.name()
            attrs.from = model.type.values()
            if (!model.required) attrs.noSelection = ["": ""]
            print attrs
            if (model.viewmode == 'readonly.') {
                attrs.disabled="True"
                zkui.select(attrs)
                return
            }
            return zkui.select(attrs)
        } else if (model.persistentProperty?.oneToOne || model.persistentProperty?.manyToOne || model.persistentProperty?.manyToMany) {
            return renderAssociationInput(model, attrs)
        } else if (model.persistentProperty?.oneToMany) {
            return renderOneToManyInput(model, attrs)
        } else if (model.type in [Date, java.util.Calendar, java.sql.Date, java.sql.Time]) {
            return renderDateTimeInput(model, attrs)
        } else if (model.type in [byte[], Byte[]]) {
            return g.field(attrs + [type: "file"])
        } else if (model.type in [TimeZone, Currency, Locale]) {
            if (!model.required) attrs.noSelection = ["": ""]
            return g."${StringUtils.uncapitalize(model.type.simpleName)}Select"(attrs)
        } else {
            return null
        }
    }

    private String renderDateTimeInput(Map model, Map attrs) {
        /*if (!model.required) {
            attrs.noSelection = ["": ""]
            attrs.default = "none"
        } */
        if (model.viewmode == 'readonly.') {
            z.label([ value: model.value, ])
            return
        }
        if (model.type == java.sql.Time) { // Datetime
            throw new Exception("Widget not implemented yet")
        }
        else { // Date only
            attrs.format="dd-MM-yyyy"
            return z.datebox(attrs)
        }
    }

    private String renderStringInput(Map model, Map attrs) {
        if (model.viewmode == 'readonly.') {
            z.label([ value: model.value, id:attrs.id])
            return
        }
        if (!attrs.type) {
            if (model.constraints.inList) {
                def select_attrs = [
                  name: attrs.name,
                  id: attrs.id ?: ((model.prefix ?: '') + model.property),
                  value: attrs.value,
                  ]

                select_attrs.from = model.constraints.inList
                if (!model.required) select_attrs.noSelection = ["": ""]
                return zkui.select(select_attrs)
            }
            else if (model.constraints.password) {
                attrs.type = "password"
                attrs.remove('value')
            }
            //else if (model.constraints.email) attrs.type = "email"
            //else if (model.constraints.url) attrs.type = "url"
            //else attrs.type = "text"
        }

        //if (model.constraints.matches) attrs.constraint = model.constraints.matches
        if (model.constraints.maxSize) attrs.maxlength = model.constraints.maxSize
        
        if (model.constraints.widget == 'textarea') {
            attrs.remove('type')
            return g.textArea(attrs)
        }
        if (attrs.weakref=='True'){
            attrs.remove('weakref')
            // Else defaults to an autocomplete combo
            def combo_attrs = [
                name: attrs.name,
                id: attrs.id ?: ((model.prefix ?: '') + model.property),
                apply: 'grails.plugin.zkfields.AssociationComposer',
                readonly: attrs.readonly?:false,
                autocomplete: attrs.autocomplete?:true,
                autodrop: attrs.autodrop?:true,
                buttonVisible: attrs.buttonVisible?:false,
             //   cols: attrs.cols,
             //   width: attrs.width,
             //   optionValue: attrs.optionValue?:'displayName',
             //   optionKey: attrs.optionKey?:'displayName',
                onChange: attrs.onChange?:'',
                onFocus: attrs.onFocus?:'',
                onCreate: attrs.onCreate?:'',
                sclass: attrs.sclass?:'',
            ]
            if (model.value) {
                combo_attrs.value = model.value
                combo_attrs.from = [ model.value ]
            }
            return z.hlayout {
                zkui.select(combo_attrs)
                z.image([
                    src: '/images/skin/database_search.png',
                    id:'btn'+combo_attrs.id])
            }
        }
        return z.textbox(attrs)
        
//      return g.field(attrs)
    }

    private String renderNumericInput(Map model, Map attrs) {
        if (model.viewmode == 'readonly.') {
            z.label([ value: model.value, id:attrs.id])
            return
        }
        if (!attrs.type && model.constraints.inList) {
            def select_attrs = [
            name: attrs.name,
            value: attrs.value,
            ]
            select_attrs.from = model.constraints.inList
            if (!model.required) attrs.noSelection = ["": ""]
            return zkui.select(attrs)
        } else if (model.type in Integer) { // integer selection
            /*Integer min, max
            if (model.constraints.range) {
                min = model.constraints.range.from
                max = model.constraints.range.to
            } else {
                min = model.constraints.min
                max = model.constraints.max
            }
            if (min && max) {
                attrs.constraint = "min $min max $max"
            } else if (min) {
                attrs.constraint = "min $min"
            } else if (max) {
                attrs.constraint = "max $max"
            }
            if (min > 0 && attrs.value == null) attrs.value = min
            else if (max < 0 && attrs.value == null) attrs.value = max */

            return z.intbox(attrs)
        } else if(model.type in Double) { //double
            return z.doublebox(attrs)
        }
        else {
            throw new Exception("Widget not implemented yet")
        }
    }

    /**
     * Defaults to an auto-complete widget
     * parameter widget can have two options:
     * - autocomplete (default)
     * - select (renders the widget as a select list
     */
    private String renderAssociationInput(Map model, Map attrs) {
        if (model.viewmode == 'readonly.') {
            z.label([ value: model.value?.displayName, id:attrs.id])
            return
        }
        print "property: " + model.property + ":"
        if (model.persistentProperty?.manyToMany) {
            throw new Exception("Relationship manyToMany not supported")
        }

        if (model.widget == 'select') {
            attrs.name = "${model.prefix?:''}${model.property}.id"
            attrs.id = attrs.id ?: ((model.prefix ?: '') + model.property)
            attrs.from = model.persistentProperty.referencedPropertyType.list()
            attrs.optionKey = attrs.optionKey?:'id'
            if (model.persistentProperty.manyToMany) {
                attrs.multiple = ""
                attrs.value = model.value*.id
            } else {
                if (!model.required) attrs.noSelection = ["null": ""]
                    attrs.value = model.value?.id
            }
            return zkui.select(attrs)
        }

        // Else defaults to an autocomplete combo
        def combo_attrs = [
            name: "${model.prefix?:''}${model.property}.id",
            id: attrs.id ?: ((model.prefix ?: '') + model.property),
            apply: 'grails.plugin.zkfields.AssociationComposer',
            readonly: attrs.readonly?:false,
            autocomplete: attrs.autocomplete?:true,
            autodrop: attrs.autodrop?:true,
            buttonVisible: attrs.buttonVisible?:false,
         //   cols: attrs.cols,
         //   width: attrs.width,
            optionValue: attrs.optionValue?:'',
            optionKey: attrs.optionKey?:'id',
            onChange: attrs.onChange?:'',
            onFocus: attrs.onFocus?:'',
            onCreate: attrs.onCreate?:'',
            sclass: model.type.toString(),
        ]
        if (model.value) {
            combo_attrs.value = model.value.id
            combo_attrs.from = [ model.value ]
        }
        z.hlayout {
            zkui.select(combo_attrs)
            z.image([
                src: '/images/skin/database_search.png',
                id:'btn'+combo_attrs.id])
        }
    }

    class SelectComboitemRenderer implements ComboitemRenderer {
        void render(Comboitem comboitem, Object o, int i) {
            comboitem.label = o.key
            comboitem.value = o.value
        }
    }





    private String renderOneToManyInput(Map model, Map attrs) {
        def buffer = new StringBuilder()
        buffer << '<ul>'
        def referencedDomainClass = model.persistentProperty.referencedDomainClass
        def controllerName = referencedDomainClass.propertyName
        attrs.value.each {
            buffer << '<li>'
            buffer << g.link(controller: controllerName, action: "show", id: it.id, it.toString().encodeAsHTML())
            buffer << '</li>'
        }
        buffer << '</ul>'
        def referencedTypeLabel = message(code: "${referencedDomainClass.propertyName}.label", default: referencedDomainClass.shortName)
        def addLabel = g.message(code: 'default.add.label', args: [referencedTypeLabel])
        buffer << g.link(controller: controllerName, action: "create", params: [("${model.beanClass.propertyName}.id".toString()): model.bean.id], addLabel)
        buffer as String
    }

}
