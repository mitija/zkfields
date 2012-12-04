package grails.plugin.zkfields

import org.zkoss.zk.ui.Component
import org.zkoss.zul.ListModelList

import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass

class AssociationComposer {
    def afterCompose = { Component comp ->
        def className = comp.sclass.substring(6) // Workaround: we pass the classname in the sclass attribute? Also we remove the 'class ' part of the name
        def grailsApplication = ApplicationHolder.application
        def params = [:]
        def mylist = []
        def currentPos = -1
        comp.addEventListener('onChanging') { e ->
            if (e.value == mylist[currentPos+1]?.key) { // We just went down
                println "down"
                currentPos++
                return
            }
            if (currentPos > 0 && e.value == mylist[currentPos-1]?.key) { // We went up
                println "up"
                currentPos--
                return
            }
            if (e.value.size() < 3) { // We reset the widget
                println "reset"
                currentPos = -1
                mylist = []
                comp.setModel(new ListModelList(mylist))  
                return
            }

            if(e.value.size() >= 3) {
                println "search"
                def instanceList = grailsApplication.getArtefact('Domain',className)?.clazz.textSearch(e.value)
                if(comp.name.substring(comp.name.length()-3,comp.name.length())=='.id'){
                    mylist = instanceList.collect { ['value':it.id, 'key': it.hasProperty('displayName')?it.displayName:it.toString() ] }
                }else{
                    mylist = instanceList.collect { ['value':it.hasProperty('displayName')?it.displayName:it.toString(), 'key': it.hasProperty('displayName')?it.displayName:it.toString() ] }
                }
                comp.setModel(new ListModelList(mylist))  
                currentPos = -1 // We reset the current position as it is a new list
            }
        }
    }
}
