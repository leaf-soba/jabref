/*
Copyright (C) 2003 Nathan Dunn, Morten O. Alver

All programs in this directory and
subdirectories are published under the GNU General Public License as
described below.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or (at
your option) any later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
USA

Further information about the GNU GPL is available at:
http://www.gnu.org/copyleft/gpl.ja.html

*/
package net.sf.jabref;

import java.util.Hashtable; 
import java.util.Enumeration ; 

public class SimpleSearchRule implements SearchRule{

    JabRefPreferences prefs;

    public SimpleSearchRule(JabRefPreferences prefs) {
	this.prefs = prefs;
    }


    public int applyRule(Hashtable searchStrings,BibtexEntry bibtexEntry) {

        int score = 0 ; 
        int counter = 0 ;

        Enumeration e = searchStrings.elements() ; 

        String searchString = (String) e.nextElement() ; 
        String upperString = null ; 
        try{
            upperString = searchString.substring(0,1).toUpperCase() + searchString.substring(1).toLowerCase() ;  
        }catch(Throwable t){
			System.err.println(t) ; 
            upperString = searchString ; 
        }

	if (prefs.getBoolean("caseSensitiveSearch")) {
	    score += doSearch(searchString,bibtexEntry) ; 
	} else {
	    score += doSearch(searchString,bibtexEntry) ; 
	    if(!searchString.equals(searchString.toLowerCase())){
		score += doSearch(searchString.toLowerCase(),bibtexEntry) ; 
	    }
	    if(!searchString.equals(searchString.toUpperCase())){
		score += doSearch(searchString.toUpperCase(),bibtexEntry) ; 
	    }
	    if(!searchString.equals(upperString)){
		score += doSearch(upperString,bibtexEntry) ; 
	    }
	}
        return score ; 
    }

    public int doSearch(String searchString, BibtexEntry bibtexEntry){
        int score = 0 ;
        int counter = 0 ; 
        //score += searchAllFields(searchString,bibtexEntry) ; 

	if (prefs.getBoolean("searchAll")) {
	    score += searchAllFields(searchString,bibtexEntry) ; 
	} else {
	
	    if (prefs.getBoolean("searchReq"))
		score += searchRequiredFields(searchString,bibtexEntry) ; 
	    if (prefs.getBoolean("searchOpt"))
		score += searchOptionalFields(searchString,bibtexEntry) ; 
	    if (prefs.getBoolean("searchGen"))
		score += searchGeneralFields(searchString,bibtexEntry) ; 
	
	}

        return score ; 
    }

    public int searchRequiredFields(String searchString,BibtexEntry bibtexEntry){
        int score = 0 ;
        int counter = 0 ; 
        String[] requiredField = bibtexEntry.getRequiredFields() ; 
	if (requiredField != null) // Some entries lack required fields.
		for(int i = 0 ; i < requiredField.length ; i++){
		    if (bibtexEntry.getField(requiredField[i]) != null)
			try{
                counter = String.valueOf(bibtexEntry.getField(requiredField[i])).indexOf(searchString,counter) ; 
                while(counter >= 0 ){
                    ++score ; 
                    counter = String.valueOf(bibtexEntry.getField(requiredField[i])).indexOf(searchString,counter+1) ; 
                }
            }catch(Throwable t ){
				System.err.println("sorting error: "+t) ; 
            }
            counter = 0 ; 
        }
        return score ; 
    }



    public int searchOptionalFields(String searchString,BibtexEntry bibtexEntry){
        int score = 0 ;
        int counter = 0 ; 
        String[] optionalField = bibtexEntry.getOptionalFields() ; 
		for(int i = 0 ; i < optionalField.length ; i++){
		    if (bibtexEntry.getField(optionalField[i]) != null)
			try{
                counter = String.valueOf(bibtexEntry.getField(optionalField[i])).indexOf(searchString,counter) ; 
                while(counter >= 0 ){
                    ++score ; 
                    counter = String.valueOf(bibtexEntry.getField(optionalField[i])).indexOf(searchString,counter+1) ; 
                }

            }catch(Throwable t ){

				System.err.println("sorting error: "+t) ; 
            }
            counter = 0 ; 
        }
        return score ; 
    }

    public int searchGeneralFields(String searchString,BibtexEntry bibtexEntry){
        int score = 0 ;
        int counter = 0 ; 
        String[] generalField = bibtexEntry.getGeneralFields() ; 
//        System.out.println("the number of general fields: " +generalField.length) ; 
		for(int i = 0 ; i < generalField.length ; i++){
		    if (bibtexEntry.getField(generalField[i]) != null)
			try{
                counter = String.valueOf(bibtexEntry.getField(generalField[i])).indexOf(searchString,counter) ; 
                while(counter >= 0 ){
                    ++score ; 
                    counter = String.valueOf(bibtexEntry.getField(generalField[i])).indexOf(searchString,counter+1) ; 
                }
            }catch(Throwable t ){
				System.err.println("sorting error: "+t) ; 
            }
            counter = 0 ; 
        }
        return score ; 
    }

    public int searchAllFields(String searchString,BibtexEntry bibtexEntry){
        int score = 0 ;
        int counter = 0 ; 
	Object[] fields = bibtexEntry.getAllFields();
        //String[] fields = GUIGlobals.ALL_FIELDS;
	for(int i = 0 ; i < fields.length ; i++){
	    if (bibtexEntry.getField(fields[i].toString()) != null)
		try{
		    counter = String.valueOf(bibtexEntry.getField(fields[i].toString())).indexOf(searchString,counter) ; 
		    while(counter >= 0 ){
			++score ; 
			counter = String.valueOf(bibtexEntry.getField(fields[i].toString())).indexOf(searchString,counter+1) ; 
		    }
		}catch(Throwable t ){
		    System.err.println("sorting error: "+t) ; 
		}
            counter = 0 ; 
        }
        return score ; 
    }


}

