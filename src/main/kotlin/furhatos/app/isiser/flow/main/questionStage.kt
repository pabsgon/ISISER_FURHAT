package furhatos.app.isiser.flow.main

import furhat.libraries.standard.NluLib
import furhatos.app.isiser.App
import furhatos.app.isiser.flow.Parent
import furhatos.app.isiser.handlers.GUIHandler
import furhatos.app.isiser.handlers.SessionHandler
import furhatos.app.isiser.nlu.*
import furhatos.app.isiser.setting.*
import furhatos.flow.kotlin.*
import furhatos.nlu.Intent
import furhatos.nlu.common.*


val QuestionReflection = state(parent = Parent) {
    val session: SessionHandler = App.getSession()
    val gui: GUIHandler = App.getGUI()
    var userDidntSpeak = true
    var userSaidMarkedIt = false
    var robotAskedIfMarked = false //This will be true the second time the robot speaks (the first time is the very first utterance)

/*
    fun Furhat.doAsk(u: Utterance){
        if(!robotAskedIfMarked)robotAskedIfMarked=true
        this.say(u)
    }
    fun Furhat.doAsk(s: String) {
        this.doAsk( utterance{s })
    }*/
    fun Furhat.askMarkingRequest(rej: EnumRejoinders) {
        robotAskedIfMarked = true
        this.ask( session.getMarkingRequest(rej) )
    }
    onEntry {
        //App.printState(thisState)
        furhat.say("Question " + session.getQuestionNumber())
        furhat.ask(session.getReflection())
    }
    onReentry {
        furhat.ask(session.getReflection())
    }


    onResponse({listOf(
        NluLib.IAmDone(),
        AnswerFalse(),
        AnswerTrue(),
        AnswerMarked(),
        RejoinderAgreed(),
        RejoinderDisagreed(),
        No(),
        Disagree(),
        Probe(),
        ElaborationRequest(),
        Maybe(),
        DontKnow(),
        Backchannel(),
        Agree(),Yes()
    )}){
        //furhat.ask(it.intent.toString())
        raise(AllIntents(it.intent as Intent))
    }

    onResponse<AllIntents>{
        val rejoinder: EnumRejoinders = it.intent.rejoinder
        furhat.say(rejoinder.toString())
        userDidntSpeak = false
        if(!userSaidMarkedIt && (rejoinder.equals(EnumRejoinders.ANSWER_MARKED) ||
                    rejoinder.equals(EnumRejoinders.ASSENT)) )userSaidMarkedIt = true
        if(userSaidMarkedIt){//We are assuming they JUST SAID they marked it now.
            if(robotAskedIfMarked){// THis "means" that the robot did answer
                if(gui.isAnswerMarked()){
                    App.goto(QuestionDisclosure(rejoinder))
                }else{
                    furhat.askMarkingRequest(rejoinder)
                    //furhat.ask(session.getMarkingRequest())
                }
            }else{// This "means" that the the robot did NOT answer
                if(gui.isAnswerMarked()){
                    App.goto(QuestionDisclosure(rejoinder))
                }else{
                    furhat.askMarkingRequest(rejoinder)
                    //furhat.ask(session.getMarkingRequest())
                }
            }
        }else{ //The user never said they marked it
            furhat.askMarkingRequest(rejoinder)
            //furhat.ask(session.getMarkingRequest())
        }
    }
    onNoResponse{
        if(userDidntSpeak){
            this.reentry()
        }else{
            raise(AllIntents(EnumRejoinders.SILENCE))
        }
    }
    /*
    onEvent<GUIEvent> {
        if(it.type == EventType.ANSWER_SENT){
            reentry()
        }else{
            propagate()
        }
    }

     */
}
fun QuestionDisclosure(lastRejoinderType: EnumRejoinders? = null) = state(parent = Parent) {
    val session: SessionHandler = App.getSession()


    onEntry {
        //App.printState(thisState)
        println("XXXDisclosure")
        furhat.ask(session.getDisclosure(lastRejoinderType))
    }
    onReentry {
        //this should never happen, but just in case:
        furhat.ask(session.getDisclosure(lastRejoinderType))
    }
    /*Intents discarded:
     I_AM_DONE*
     RejoinderDisagreed
     AnswerMarked

     */

    onResponse({listOf(
        AnswerFalse(),
        AnswerTrue(),
        RejoinderAgreed(),
        RejoinderDisagreed(),
        No(),
        Disagree(),
        Probe(),
        ElaborationRequest(),
        Maybe(),
        DontKnow(),
        Backchannel(),
        Agree(),Yes()
    )}){
        raise(AllIntents(it.intent as Intent))
    }

    onResponse<AllIntents> {
        var rejoinder: EnumRejoinders = it.intent.rejoinder
        rejoinder = session.impliedRejoinder(rejoinder)
        furhat.say(rejoinder.toString())
        when(rejoinder){

            EnumRejoinders.REJOINDER_AGREED, EnumRejoinders.REJOINDER_DISAGREED,
            EnumRejoinders.PROBE, EnumRejoinders.ELABORATION_REQUEST, EnumRejoinders.NON_COMMITTAL,
            EnumRejoinders.BACKCHANNEL -> {
                if(session.inOfficialAgreement()){
                    // This means that the robot answer and the MARKED answer coincide
                    App.goto(QuestionReview(rejoinder))
                }else{
                    App.goto(QuestionPersuasion(rejoinder))
                }
            }

            EnumRejoinders.DENIAL -> {
                App.goto(QuestionPersuasion(rejoinder))
            }

            EnumRejoinders.ASSENT -> {
                if(session.inOfficialAgreement()){
                    // This means that the robot answer and the MARKED answer coincide
                    App.goto(QuestionReview(rejoinder))
                }else{
                    App.goto(QuestionCheckpoint(rejoinder))
                }
            }
            else -> {
                /* The rest of possible values should not occur for different reasons:
                EnumRejoinders.SILENCE -> {/*Handled in onNoResponse*/}
                EnumRejoinders.TIME_REQUEST, EnumRejoinders.OFF_TOPIC,
                EnumRejoinders.REPEAT_REQUEST -> {/*Handled in parent state*/}
                EnumRejoinders.I_AM_DONE, EnumRejoinders.ANSWER_MARKED ->  {/*Not captured in onResponse above*/}
                EnumRejoinders.ANSWER_TRUE,
                EnumRejoinders.ANSWER_FALSE They are converted to ASSENT or DENIAL by calling impliedRejoinder

                Therefore tHis should never happen, but just in case:
                 */

                furhat.ask("Uhm")
            }

        }


    }

    onNoResponse{
        if(session.inOfficialAgreement()){
            // This means that the robot answer and the MARKED answer coincide
            App.goto(QuestionCheckpoint(EnumRejoinders.SILENCE))
        }else{
            App.goto(QuestionPersuasion(EnumRejoinders.SILENCE))
        }
    }
    /*
    onEvent<GUIEvent> {
        if(it.type == EventType.ANSWER_SENT){
            reentry()
        }else{
            propagate()
        }
    }

     */
}
fun QuestionPersuasion(lastRejoinderType: EnumRejoinders? = null): State  = state(parent = Parent) {
    val session: SessionHandler = App.getSession()

    fun Furhat.sayUnfriendlyClaimOrGetOut(rej: EnumRejoinders? = null){
        if(session.thereAreUnfriendlyClaims()) {
            this.ask(session.getUnfriendlyClaim(rej))
        }else{
            if(session.neverAskedInCheckpoint()){
                App.goto(QuestionCheckpoint(rej))
            }else{
                if(session.inCompleteAgreement()){
                    //If they are in verbal agreement and they also agree with marked answer
                }else{
                    App.goto(QuestionUltimatum(rej))
                }
            }
        }
    }

    fun Furhat.gotoCheckPointOrReview(rej: EnumRejoinders? = null){
        if(session.inVerbalAgreement()){
            App.goto(QuestionReview(rej))
        }else{
            App.goto(QuestionCheckpoint(rej))
        }
    }
    onEntry {
        // When entering this state, the student and robot should be in disagreement. There's a function
        // that calculates this based on the user's verbal and marked answers and the robot's answer.
        // Based on this, the right claim (friendly or unfriendly) could be extracted based on this info.
        // However, for security, once in this state the type of claim will be asked explicitly.
        //App.printState(thisState)
        // ADD A WARNING LINE: if(session.inAgreement()) warn "Both in agreement in persuasion state is unexpected"
        furhat.say("Persuasion!")
        furhat.sayUnfriendlyClaimOrGetOut(lastRejoinderType)

    }
    onReentry {
        //App.printState(thisState,"R")//
        /*if(session.thereAreUnfriendlyClaims()) {
            furhat.ask(session.getUnfriendlyClaim())
        }else{
            App.goto(QuestionUltimatum())
        }*/
    }

    onResponse({listOf(
        NluLib.IAmDone(),
        AnswerFalse(),
        AnswerTrue(),
        AnswerMarked(),
        RejoinderAgreed(),
        RejoinderDisagreed(),
        No(),
        Disagree(),
        Probe(),
        ElaborationRequest(),
        Maybe(),
        DontKnow(),
        Backchannel(),
        Agree(),Yes()
    )}){
        raise(AllIntents(it.intent as Intent))
    }
    onResponse<AllIntents> {
        var rejoinder: EnumRejoinders = it.intent.rejoinder
        furhat.say(rejoinder.toString())
        rejoinder = session.impliedRejoinder(rejoinder) //This will convert ANSWER_TRUE or ANSWER_FALSE into ASSENT or DENIAL, when proceeds.
        when(rejoinder){

            EnumRejoinders.REJOINDER_AGREED, EnumRejoinders.REJOINDER_DISAGREED,
            EnumRejoinders.PROBE, EnumRejoinders.ELABORATION_REQUEST,
            EnumRejoinders.NON_COMMITTAL, EnumRejoinders.BACKCHANNEL,
            EnumRejoinders.SILENCE -> furhat.sayUnfriendlyClaimOrGetOut(rejoinder)

            EnumRejoinders.DENIAL -> {
                if(USE_PROBES_AT_DISCUSSION && !session.maxNumOfUnfriendlyProbesReached()){
                    furhat.ask(session.getUnfriendlyProbe(rejoinder))
                }else{
                    furhat.sayUnfriendlyClaimOrGetOut(rejoinder)
                }
            }

            EnumRejoinders.I_AM_DONE, EnumRejoinders.ASSENT -> furhat.gotoCheckPointOrReview(rejoinder)

            else -> { // This should not happen, but just in case:
                furhat.ask("Uhm")
            }
        }

    }
    onNoResponse{
        raise(AllIntents(EnumRejoinders.SILENCE))
    }
    /*
    onEvent<GUIEvent> {
        if(it.type == EventType.ANSWER_SENT){
            reentry()
        }else{
            propagate()
        }
    }
     */
}
fun QuestionReview(lastRejoinderType: EnumRejoinders? = null): State = state(parent = Parent) {
    // If this state has been reached, is because they agree.
    // We're assuming that there's no turning back to disagreement, although it's technically possible
    // and the system should allow it.
    val session: SessionHandler = App.getSession()

    fun Furhat.sayFriendlyClaimOrGetOut(rej: EnumRejoinders? = null){
        if(session.thereAreFriendlyClaims()) {
            this.ask(session.getFriendlyClaim(rej))
        }else{
            if(session.neverAskedInCheckpoint()){
                App.goto(QuestionCheckpoint(rej))
            }else{
                if(session.inCompleteAgreement()){
                    //If they are in verbal agreement and they also agree with marked answer
                    App.goto(QuestionConfirmation(rej))
                }else{
                    App.goto(QuestionUltimatum(rej))
                }
            }
        }
    }
    onEntry {
        //App.printState(thisState)
        furhat.say("Review!")
        furhat.sayFriendlyClaimOrGetOut(lastRejoinderType)
    }
    onReentry() {
        //App.printState(thisState,"R")//
    }
    onResponse({listOf(
        NluLib.IAmDone(),
        AnswerFalse(),
        AnswerTrue(),
        AnswerMarked(),
        RejoinderAgreed(),
        RejoinderDisagreed(),
        No(),
        Disagree(),
        Probe(),
        ElaborationRequest(),
        Maybe(),
        DontKnow(),
        Backchannel(),
        Agree(),Yes()
    )}){
        raise(AllIntents(it.intent as Intent))
    }

    onResponse<AllIntents> {
        var rejoinder: EnumRejoinders = it.intent.rejoinder
        furhat.say(rejoinder.toString())
        rejoinder = session.impliedRejoinder(rejoinder) //This will convert ANSWER_TRUE or ANSWER_FALSE into ASSENT or DENIAL, when proceeds.
        when(rejoinder){



            EnumRejoinders.DENIAL -> {
                if(USE_PROBES_AT_DISCUSSION && !session.maxNumOfUnfriendlyProbesReached()){
                    furhat.ask(session.getFriendlyProbe(rejoinder))
                }else{
                    furhat.sayFriendlyClaimOrGetOut(rejoinder)
                }

            }

            EnumRejoinders.I_AM_DONE, EnumRejoinders.ASSENT,
            EnumRejoinders.REJOINDER_AGREED, EnumRejoinders.REJOINDER_DISAGREED,
            EnumRejoinders.PROBE, EnumRejoinders.ELABORATION_REQUEST,
            EnumRejoinders.NON_COMMITTAL, EnumRejoinders.BACKCHANNEL,
            EnumRejoinders.SILENCE -> furhat.sayFriendlyClaimOrGetOut(rejoinder)

            else -> { // This should not happen, but just in case:
                furhat.ask("Uhm")
            }
        }


    }

    onNoResponse{
        raise(AllIntents(EnumRejoinders.SILENCE))
    }
    /*
    onEvent<GUIEvent> {
        if(it.type == EventType.ANSWER_SENT){
            reentry()
        }else{
            propagate()
        }
    }

     */
}
fun QuestionCheckpoint(lastRejoinderType: EnumRejoinders? = null, beFriendly: Boolean? = null): State  = state(parent = Parent) {
/*    STATE ENTRY: This state is called at several moments. The checkpoints statement should be added in order of clarity.
This is so because the state will call itself, and will repeat exactly the same logic. The only difference should be the
utterance. Since texts are designed to be extracted from a list and moved to the end, utterances should be different every
 time, if several texts were added. In all cases, the expected answer must be true/false, with TRUE meaning that the user
 aligns with the robot.
The main difference between Checkpoint and ultimatum is that 1) Ultimatum is never friendly (it was reached with
disagreement) and 2) there's no coming back.

This state determines if the user is in verbal agreement or not. userVerballyAgrees is set here to TRUE or FALSE.
The implications are mostly REVIEW and PERSUASION, where it can be checked if the user did pass through CHECKPOINT so
they don't have to go again, and proceed with ULTIMATUM instead.


    if beFriendly or inOfficialAgreement (friendly)
    we agree with #ROBOT_ANSWER# as we discussed?
    shall we go with #ROBOT_ANSWER# as we suggest?

    if not befriendly Not inOfficialAgreement (unfriendly)
    you agree with #ROBOT_ANSWER# as I suggest?
    shall we go with #ROBOT_ANSWER# as I suggest?
*/
    val session: SessionHandler = App.getSession()

    fun Furhat.sayCheckpoint(rej: EnumRejoinders? = null){
        this.ask(session.getCheckpoint(rej))
    }
    onEntry {
        //App.printState(thisState)
        println("XXXDisclosure")
        furhat.say("Checkpoint!")
        furhat.sayCheckpoint(lastRejoinderType)
    }
    onReentry {
    }

    onResponse({listOf(
        NluLib.IAmDone(),
        AnswerFalse(),
        AnswerTrue(),
        AnswerMarked(),
        RejoinderAgreed(),
        RejoinderDisagreed(),
        No(),
        Disagree(),
        Probe(),
        ElaborationRequest(),
        Maybe(),
        DontKnow(),
        Backchannel(),
        Agree(),Yes()
    )}){
        raise(AllIntents(it.intent as Intent))
    }

    onResponse<AllIntents> {
        var rejoinder: EnumRejoinders = it.intent.rejoinder
        furhat.say(rejoinder.toString())
        rejoinder = session.impliedRejoinder(rejoinder) //This will convert ANSWER_TRUE or ANSWER_FALSE into ASSENT or DENIAL, when proceeds.
        when(rejoinder){

            EnumRejoinders.REJOINDER_AGREED, EnumRejoinders.REJOINDER_DISAGREED,
            EnumRejoinders.PROBE, EnumRejoinders.NON_COMMITTAL ,
            EnumRejoinders.DENIAL -> {
                session.userVerballyDisagrees()
                App.goto(QuestionPersuasion(rejoinder))
            }
            EnumRejoinders.ELABORATION_REQUEST,EnumRejoinders.BACKCHANNEL,
            EnumRejoinders.SILENCE -> furhat.sayCheckpoint(rejoinder)

            EnumRejoinders.I_AM_DONE, EnumRejoinders.ASSENT ->{
                session.userVerballyAgrees()
                App.goto(QuestionReview(rejoinder))
            }

            else -> { // This should not happen, but just in case:
                furhat.ask("Uhm")
            }
        }
    }

    onNoResponse{
        raise(AllIntents(EnumRejoinders.SILENCE))
    }
    /*
    onEvent<GUIEvent> {
        if(it.type == EventType.ANSWER_SENT){
            reentry()
        }else{
            propagate()
        }
    }

     */
}
fun QuestionUltimatum(lastRejoinderType: EnumRejoinders? = null) = state(parent = Parent) {
    val session: SessionHandler = App.getSession()

    fun Furhat.sayUltimatum(rej: EnumRejoinders? = null){
        this.ask(session.getUltimatum(rej))
    }
    onEntry {
        //App.printState(thisState)
        furhat.say("Ultimatum!")
        furhat.sayUltimatum(lastRejoinderType)
    }
    onReentry {
    }

    onResponse({listOf(
        NluLib.IAmDone(),
        AnswerFalse(),
        AnswerTrue(),
        AnswerMarked(),
        RejoinderAgreed(),
        RejoinderDisagreed(),
        No(),
        Disagree(),
        Probe(),
        ElaborationRequest(),
        Maybe(),
        DontKnow(),
        Backchannel(),
        Agree(),Yes()
    )}){
        raise(AllIntents(it.intent as Intent))
    }

    onResponse<AllIntents> {
        var rejoinder: EnumRejoinders = it.intent.rejoinder
        furhat.say(rejoinder.toString())
        when(rejoinder){

            EnumRejoinders.ANSWER_FALSE, EnumRejoinders.ANSWER_TRUE ->{
                session.setRobotFinalAnswer(rejoinder.getAnswer())
                App.goto(QuestionConfirmation(rejoinder))
            }

            else -> { // This should not happen, but just in case:
                furhat.sayUltimatum(rejoinder)
            }
        }
    }

    onNoResponse{
        raise(AllIntents(EnumRejoinders.SILENCE))
    }
}

fun QuestionConfirmation(lastRejoinderType: EnumRejoinders? = null) = state(parent = Parent) {
    val session: SessionHandler = App.getSession()
    val gui: GUIHandler = App.getGUI()
    var userSaidMarkedIt = false

    fun Furhat.askConfirmationRequest1st(rej: EnumRejoinders?) {
        this.ask( session.get1stConfirmationRequest(rej) )
    }
    fun Furhat.askConfirmationRequest2nd(rej: EnumRejoinders?) {
        this.ask( session.get2ndConfirmationRequest() )
    }
    onEntry {
        //App.printState(thisState)
        furhat.askConfirmationRequest1st(lastRejoinderType)
    }
    onReentry {
    }


    onResponse({listOf(
        NluLib.IAmDone(),
        AnswerFalse(),
        AnswerTrue(),
        AnswerMarked(),
        RejoinderAgreed(),
        RejoinderDisagreed(),
        No(),
        Disagree(),
        Probe(),
        ElaborationRequest(),
        Maybe(),
        DontKnow(),
        Backchannel(),
        Agree(),Yes()
    )}){
        //furhat.ask(it.intent.toString())
        raise(AllIntents(it.intent as Intent))
    }
    onResponse(AnswerMarked()){
        raise(AllIntents(it.intent as Intent))
    }

    onResponse<AllIntents>{
        var rejoinder: EnumRejoinders = it.intent.rejoinder
        furhat.say(rejoinder.toString())
        when(rejoinder){
            EnumRejoinders.ANSWER_MARKED, EnumRejoinders.ASSENT ->{
                userSaidMarkedIt = true
                furhat.askConfirmationRequest2nd(rejoinder)
            }

            else -> {
                if(userSaidMarkedIt){
                    furhat.askConfirmationRequest2nd(rejoinder)
                }else {
                    furhat.askConfirmationRequest1st(rejoinder)
                }
            }
        }
    }
    onNoResponse{
        raise(AllIntents(EnumRejoinders.SILENCE))
    }
}
