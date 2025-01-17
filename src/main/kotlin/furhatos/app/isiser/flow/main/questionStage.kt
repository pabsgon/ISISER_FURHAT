package furhatos.app.isiser.flow.main

import furhatos.app.isiser.App
import furhatos.app.isiser.flow.Parent
import furhatos.app.isiser.handlers.*
import furhatos.app.isiser.nlu.*
import furhatos.app.isiser.setting.*
import furhatos.flow.kotlin.*
import furhatos.nlu.Intent

fun QuestionReflection( ) = state(parent = Parent) {
    /*
    * Here the mission of the robot is to know if the user answered or not.
    * The user could
    *   CASE 1: say the answered ("I have it" --> IAmDone)
    *        Goto QuestionMarking
    *   CASE 2: say the answer ("It's true" --> ANSWER_TRUE)
    *        Register the verbal answer.
    *        Goto QuestionMarking
    *   CASE 3: SAY they marked the answer.
    *        Goto QuestionDisclosure
    * */
    val session: SessionHandler = App.getSession()
    val gui: GUIHandler = App.getGUI()
    var userDidntTalk = true
    var userAnswered = false
    var userSaidThatMarked = false
    var robotFriendliness = EnumFriendliness.UNFRIENDLY
    var friendlyAllowedTimes = 2

    fun setFriendliness():EnumFriendliness{
        return if(friendlyAllowedTimes>0) {
            friendlyAllowedTimes--
            EnumFriendliness.FRIENDLY
        }else EnumFriendliness.UNFRIENDLY
    }

    onEntry {
        furhat.doAsk(session.getUtterance(EnumWordingTypes.QUESTION_RECEPTION),null, REFLECTION_TIMEOUT )
    }
    onReentry {
        if(gui.isAnswerMarked()){
            furhat.doAsk(session.getUtterance(EnumWordingTypes.ANSWERING_REQUEST, null, robotFriendliness), null, WAITING_ANSWER_AFTER_ANSWER_MARKED_TIMEOUT)
        }else{
            furhat.doAsk(session.getUtterance(EnumWordingTypes.ANSWERING_REQUEST, null, robotFriendliness))
        }
    }

    onResponse<AllIntents>{
        val rejoinder: EnumRejoinders = it.intent.rejoinder
        userDidntTalk = if(rejoinder==EnumRejoinders.SILENCE) userDidntTalk else false
        if(userDidntTalk){

            if(gui.isAnswerMarked()){
                App.goto(QuestionDisclosure(rejoinder))
            }else {
                reentry()
            }
        }else{
            userDidntTalk = false
            when(rejoinder){
                EnumRejoinders.ANSWER_TRUE,
                EnumRejoinders.ANSWER_FALSE ->{session.setUserVerbalAnswer(rejoinder)
                                               userAnswered = true}

                EnumRejoinders.ME_READY -> { userAnswered = true }

                EnumRejoinders.ANSWER_MARKED ->{userSaidThatMarked = true
                                                userAnswered = true}
                EnumRejoinders.ASSENT -> userAnswered = true

                else -> {
                    if(gui.isAnswerMarked()){
                        userAnswered = true
                    }
                    setFriendliness()
                }
/*
                EnumRejoinders.I_LIKE_MY_ANSWER,
                EnumRejoinders.I_LIKE_YOUR_ANSWER,
                EnumRejoinders.DENIAL,
                EnumRejoinders.REJOINDER_AGREED,
                EnumRejoinders.REJOINDER_DISAGREED,
                EnumRejoinders.PROBE,
                EnumRejoinders.ELABORATION_REQUEST,
                EnumRejoinders.NON_COMMITTAL,
                EnumRejoinders.BACKCHANNEL -> {
                    if(gui.isAnswerMarked()){
                        userAnswered = true
                    }
                    setFriendliness()
                }
                EnumRejoinders.SILENCE -> {setFriendliness()}

                else -> { raise(OffTopic())}
*/
            }
            if(userAnswered) {
                if(gui.isAnswerMarked()){
                    App.goto(QuestionDisclosure(rejoinder))
                }else{
                    App.goto(QuestionMarking(rejoinder, userSaidThatMarked))
                }
            }else{
                if(gui.isAnswerMarked()){
                    furhat.doAsk(session.getUtterance(EnumWordingTypes.ANSWERING_REQUEST, rejoinder, robotFriendliness), null, WAITING_ANSWER_AFTER_ANSWER_MARKED_TIMEOUT)
                }else{
                    furhat.doAsk(session.getUtterance(EnumWordingTypes.ANSWERING_REQUEST, rejoinder, robotFriendliness))
                }
            }
        }
    }
}

fun QuestionMarking(lastRejoinderType: EnumRejoinders? , userSaidThatMarked: Boolean = false) = state(parent = Parent) {
    /*
    * Here the mission of the robot is to ensure that the user marks the answer
    * The user could
    *   CASE 1: say the answered ("I have it" --> IAmDone)
    *        Goto QuestionMarking
    *   CASE 2: say the answer ("It's true" --> ANSWER_TRUE)
    *        Register the verbal answer.
    *        Goto QuestionMarking
    *   CASE 3: SAY they marked the answer.
    *        Goto QuestionDisclosure
    * */
    val session: SessionHandler = App.getSession()
    val gui: GUIHandler = App.getGUI()
    var userSaidMarkedIt = userSaidThatMarked

    fun getFriendliness():EnumFriendliness {
        return if(userSaidMarkedIt) EnumFriendliness.UNFRIENDLY else EnumFriendliness.FRIENDLY
    }

    onEntry {
        furhat.doAsk( session.getUtterance(EnumWordingTypes.MARKING_REQUEST, lastRejoinderType, getFriendliness() ))
    }


    onResponse<AllIntents>{
        val rejoinder: EnumRejoinders = it.intent.rejoinder
        when(rejoinder){
            EnumRejoinders.ANSWER_TRUE,
            EnumRejoinders.ANSWER_FALSE -> {session.setUserVerbalAnswer(rejoinder) }
            EnumRejoinders.ME_READY,
            EnumRejoinders.ANSWER_MARKED, EnumRejoinders.ASSENT  -> { userSaidMarkedIt = true}

            EnumRejoinders.I_LIKE_MY_ANSWER,
            EnumRejoinders.I_LIKE_YOUR_ANSWER,
            EnumRejoinders.DENIAL,
            EnumRejoinders.REJOINDER_AGREED,
            EnumRejoinders.REJOINDER_DISAGREED,
            EnumRejoinders.PROBE,
            EnumRejoinders.ELABORATION_REQUEST,
            EnumRejoinders.NON_COMMITTAL,
            EnumRejoinders.BACKCHANNEL,
            EnumRejoinders.SILENCE -> {}
            else -> {/*Handled in parent*/}
        }
        if(gui.isAnswerMarked()){
            App.goto(QuestionDisclosure(rejoinder))
        }else{
            furhat.doAsk( session.getUtterance(EnumWordingTypes.MARKING_REQUEST, lastRejoinderType, getFriendliness() ))
        }
    }
}

fun QuestionDisclosure(lastRejoinderType: EnumRejoinders? ) = state(parent = Parent) {
    val session: SessionHandler = App.getSession()


    onEntry {
        session.setRobotAnswerAsDisclosed()
        if(session.isUserVerballyUndecided()) {
            furhat.doAsk(session.getUtterance(EnumWordingTypes.DISCLOSURE_EARLY, lastRejoinderType))
        }else{
            furhat.doAsk(session.getUtterance(EnumWordingTypes.DISCLOSURE_LATE,
                lastRejoinderType,
                if(session.inVerbalAgreement()) EnumFriendliness.FRIENDLY else EnumFriendliness.UNFRIENDLY
                ))
        }
    }


    onResponse<AllIntents> {
        var rejoinder: EnumRejoinders = it.intent.rejoinder
        rejoinder = session.impliedAssentOrDenial(rejoinder)
        /** Here
         *
         *  EnumRejoinders.ANSWER_FALSE
         *  EnumRejoinders.ANSWER_TRUE
         *  EnumRejoinders.I_LIKE_MY_ANSWER
         *  EnumRejoinders.I_LIKE_YOUR_ANSWER
         *
         *  are converted to ASSENT or DENIAL
         **/

        when(rejoinder){

            EnumRejoinders.REJOINDER_AGREED, EnumRejoinders.REJOINDER_DISAGREED,
            EnumRejoinders.PROBE, EnumRejoinders.ELABORATION_REQUEST, EnumRejoinders.NON_COMMITTAL,
            EnumRejoinders.SILENCE, EnumRejoinders.BACKCHANNEL -> {
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

            EnumRejoinders.ME_READY,
            EnumRejoinders.ANSWER_MARKED -> { raise(AllIntents(EnumRejoinders.OFF_TOPIC))}
            else -> { raise(OffTopic())}
        }


    }
}
fun QuestionPersuasion(lastRejoinderType: EnumRejoinders? = null): State  = state(parent = Parent) {
    val session: SessionHandler = App.getSession()



    fun Furhat.sayUnfriendlyClaimOrGetOut(rej: EnumRejoinders? = null){
        if(session.thereAreUnfriendlyClaims()) {
            /*this.doAsk(session.getUtterance(EnumWordingTypes.CLAIM, rej, EnumFriendliness.UNFRIENDLY) )*/
            this.doAsk(session.getUtterance(EnumWordingTypes.CLAIM, rej, EnumFriendliness.UNFRIENDLY), null, WAITING_ANSWER_TIMEOUT_CLAIMS)
        }else{
            if(session.wasCheckpointReached()){
                if(session.inCompleteAgreement()){
                    //If they are in verbal agreement and they also agree with marked answer
                    /*this.doAsk("ALERT: this is a situation that it is not handled.")
                    TODO ()*/
                    App.goto(QuestionConfirmation(rej))
                }else{
                    App.goto(QuestionUltimatum(rej))
                }
            }else{
                App.goto(QuestionCheckpoint(rej))
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
        // However, for security, once in this state the type of claim will b   e asked explicitly.
        //App.printState(thisState)
        // ADD A WARNING LINE: if(session.inAgreement()) warn "Both in agreement in persuasion state is unexpected"
        //furhat.doSay("Persuasion!")
        furhat.sayUnfriendlyClaimOrGetOut(lastRejoinderType)

    }

    onResponse<OffTopic>{
        furhat.doAsk(session.getUtterance(EnumWordingTypes.ELABORATION_REQUEST))
    }
    onResponse<AllIntents> {
        var rejoinder: EnumRejoinders = it.intent.rejoinder
        rejoinder = session.impliedAssentOrDenial(rejoinder) //This will convert ANSWER_TRUE or ANSWER_FALSE into ASSENT or DENIAL, when proceeds.
        /** Here
         *
         *  EnumRejoinders.ANSWER_FALSE
         *  EnumRejoinders.ANSWER_TRUE
         *  EnumRejoinders.I_LIKE_MY_ANSWER
         *  EnumRejoinders.I_LIKE_YOUR_ANSWER
         *
         *  are converted to ASSENT or DENIAL
         **/
        when(rejoinder){
            EnumRejoinders.REJOINDER_AGREED, EnumRejoinders.REJOINDER_DISAGREED,
            EnumRejoinders.PROBE, EnumRejoinders.ELABORATION_REQUEST,
            EnumRejoinders.NON_COMMITTAL, EnumRejoinders.BACKCHANNEL,
            EnumRejoinders.SILENCE -> furhat.sayUnfriendlyClaimOrGetOut(rejoinder)

            EnumRejoinders.DENIAL -> {
                if(!session.maxNumOfUnfriendlyProbesReached()){
                    furhat.doAsk(session.getUtterance(EnumWordingTypes.PROBE, rejoinder, EnumFriendliness.UNFRIENDLY))
                }else{
                    furhat.sayUnfriendlyClaimOrGetOut(rejoinder)
                }
            }

            EnumRejoinders.ASSENT -> if(session.wasLastClaimAssentSensitive()){
                furhat.sayUnfriendlyClaimOrGetOut(rejoinder)
            }else{
                furhat.gotoCheckPointOrReview(rejoinder)
            }

            EnumRejoinders.ME_READY, EnumRejoinders.ANSWER_MARKED -> { raise(AllIntents(EnumRejoinders.OFF_TOPIC))}
            else -> { raise(OffTopic())} //OFF_TOPIC handled by Parent
        }
    }
}



fun QuestionReview(lastRejoinderType: EnumRejoinders? = null): State = state(parent = Parent) {
    // If this state has been reached, is because they agree.
    // We're assuming that there's no turning back to disagreement, although it's technically possible
    // and the system should allow it.
    val session: SessionHandler = App.getSession()

    fun Furhat.sayFriendlyClaimOrGetOut(rej: EnumRejoinders? = null){
        if(session.thereAreFriendlyClaims()) {
            /*this.doAsk(session.getUtterance(EnumWordingTypes.CLAIM, rej, EnumFriendliness.FRIENDLY))*/
            this.doAsk(session.getUtterance(EnumWordingTypes.CLAIM, rej, EnumFriendliness.FRIENDLY), null, WAITING_ANSWER_TIMEOUT_CLAIMS)
        }else{
            if(session.wasCheckpointReached()){
                if(session.inCompleteAgreement()){
                    //If they are in verbal agreement and they also agree with marked answer
                    App.goto(QuestionConfirmation(rej))
                }else{
                    App.goto(QuestionUltimatum(rej))
                }
            }else{
                App.goto(QuestionCheckpoint(rej))
            }
        }
    }
    onEntry {
        furhat.sayFriendlyClaimOrGetOut(lastRejoinderType)
    }

    onResponse<AllIntents> {
        var rejoinder: EnumRejoinders = it.intent.rejoinder
        rejoinder = session.impliedAssentOrDenial(rejoinder) //This will convert ANSWER_TRUE or ANSWER_FALSE into ASSENT or DENIAL, when proceeds.
        /** Here
         *
         *  EnumRejoinders.ANSWER_FALSE
         *  EnumRejoinders.ANSWER_TRUE
         *  EnumRejoinders.I_LIKE_MY_ANSWER
         *  EnumRejoinders.I_LIKE_YOUR_ANSWER
         *
         *  are converted to ASSENT or DENIAL
         **/

        when(rejoinder){

            EnumRejoinders.DENIAL -> {
                if(!session.maxNumOfFriendlyProbesReached()){
                    furhat.doAsk(session.getUtterance(EnumWordingTypes.PROBE, rejoinder, EnumFriendliness.FRIENDLY))
                }else{
                    furhat.sayFriendlyClaimOrGetOut(rejoinder)
                }
            }

            EnumRejoinders.ME_READY, EnumRejoinders.ASSENT,
            EnumRejoinders.REJOINDER_AGREED, EnumRejoinders.REJOINDER_DISAGREED,
            EnumRejoinders.PROBE, EnumRejoinders.ELABORATION_REQUEST,
            EnumRejoinders.NON_COMMITTAL, EnumRejoinders.BACKCHANNEL,
            EnumRejoinders.SILENCE -> furhat.sayFriendlyClaimOrGetOut(rejoinder)



            EnumRejoinders.ANSWER_MARKED -> { raise(OffTopic())}
            else -> { raise(OffTopic())}
        }


    }
}
fun QuestionCheckpoint(lastRejoinderType: EnumRejoinders? = null, beFriendly: Boolean? = null): State  = state(parent = Parent) {
/**    STATE ENTRY: This state is called at several moments. The checkpoints statement should be added in order of clarity.
This is so because the state will call itself, and will repeat exactly the same logic. The only difference should be the
utterance. Since texts are designed to be extracted from a list and moved to the end, utterances should be different every
 time, if several texts were added. In all cases, the expected answer must be true/false, with TRUE meaning that the user
 aligns with the robot.
The main difference between Checkpoint and ultimatum is that 1) Ultimatum is never friendly (it was reached with
disagreement) and 2) there's no coming back.

This state determines if the user is in verbal agreement or not. This can also happen during QuestionReflection and
QuestionDisclosure. However, it won't happen during Persuasion and Review, since an assent to a robot proposition will
lead them here to Checkpoint. userVerballyAgrees is set here to TRUE or FALSE.
The implications are firstly in DISCLOSURE, but later in REVIEW and PERSUASION, where it can be checked if the user did
pass through CHECKPOINT, so they don't have to go again, and proceed with ULTIMATUM instead.


    if beFriendly or inOfficialAgreement (friendly)
    we agree with #ROBOT_ANSWER# as we discussed?
    shall we go with #ROBOT_ANSWER# as we suggest?

    if not befriendly Not inOfficialAgreement (unfriendly)
    you agree with #ROBOT_ANSWER# as I suggest?
    shall we go with #ROBOT_ANSWER# as I suggest?
*/
    val session: SessionHandler = App.getSession()


    onEntry {
        session.setUserAnswerToBeConfirmed()
        session.checkpointReached()
        //sessionHandler will take care of the friendliness mode of the utterance.
        furhat.doAsk(session.getUtterance(EnumWordingTypes.CHECKPOINT, lastRejoinderType))
    }


    onResponse<AllIntents> {
        var rejoinder: EnumRejoinders = it.intent.rejoinder
        //furhat.doSay(rejoinder.toString())
        rejoinder = session.impliedAssentOrDenial(rejoinder) //This will convert ANSWER_TRUE or ANSWER_FALSE into ASSENT or DENIAL, when proceeds.
        when(rejoinder){
            EnumRejoinders.REJOINDER_AGREED, EnumRejoinders.REJOINDER_DISAGREED,
            EnumRejoinders.PROBE, EnumRejoinders.NON_COMMITTAL ,
            EnumRejoinders.DENIAL -> {
                session.setUserVerballyDisagrees()
                App.goto(QuestionPersuasion(rejoinder))
            }
            EnumRejoinders.ELABORATION_REQUEST,EnumRejoinders.BACKCHANNEL,
            EnumRejoinders.SILENCE ->
                furhat.doAsk(session.getUtterance(EnumWordingTypes.CHECKPOINT, rejoinder))

            EnumRejoinders.ME_READY, EnumRejoinders.ASSENT ->{
                session.setUserVerballyAgrees()
                App.goto(QuestionReview(rejoinder))
            }


            EnumRejoinders.ANSWER_MARKED -> { raise(OffTopic())}

            else -> { raise(OffTopic())}
        }
    }
}
fun QuestionUltimatum(lastRejoinderType: EnumRejoinders? = null) = state(parent = Parent) {
    val session: SessionHandler = App.getSession()

    onEntry {
        //App.printState(thisState)
        //furhat.doSay("Ultimatum!")
        //sessionHandler will take care of the friendliness mode of the utterance.

        session.setUserAnswerToBeConfirmed()
        furhat.doAsk(session.getUtterance(EnumWordingTypes.ULTIMATUM, lastRejoinderType,))
    }


    onResponse<AllIntents> {
        var rejoinder: EnumRejoinders = it.intent.rejoinder
        //furhat.doSay(rejoinder.toString())
        rejoinder = session.impliedAnswerRejoinder(rejoinder)
        /** Here
         *  EnumRejoinders.I_LIKE_MY_ANSWER
         *  EnumRejoinders.I_LIKE_YOUR_ANSWER
         *
         *  are converted to ANSWER_FALSE or ANSWER_TRUE
         **/

        when(rejoinder){
            EnumRejoinders.ANSWER_FALSE, EnumRejoinders.ANSWER_TRUE ->{

                session.setRobotFinalAnswer(rejoinder.getAnswer())
                App.goto(QuestionConfirmation(rejoinder))
            }
            EnumRejoinders.ME_READY,
            EnumRejoinders.ANSWER_MARKED,
            EnumRejoinders.ASSENT ,
            EnumRejoinders.DENIAL,
            EnumRejoinders.REJOINDER_AGREED,
            EnumRejoinders.REJOINDER_DISAGREED,
            EnumRejoinders.PROBE,
            EnumRejoinders.ELABORATION_REQUEST,
            EnumRejoinders.NON_COMMITTAL,
            EnumRejoinders.BACKCHANNEL,
            EnumRejoinders.SILENCE -> {
                furhat.doAsk(session.getUtterance(EnumWordingTypes.ULTIMATUM, rejoinder))
            }
            else -> {
                raise(OffTopic())
            }

        }
    }
}

fun QuestionConfirmation(lastRejoinderType: EnumRejoinders? = null) = state(parent = Parent) {
    val session: SessionHandler = App.getSession()
    var userSaidMarkedIt = false

    onEntry {
        furhat.doAsk( session.getUtterance(EnumWordingTypes.CONFIRMATION_REQUEST,lastRejoinderType,EnumFriendliness.FRIENDLY) )
    }



    onResponse(AnswerMarked()){
        raise(AllIntents(it.intent as Intent))
    }

    onResponse<AllIntents>{
        var rejoinder: EnumRejoinders = it.intent.rejoinder
        //furhat.doSay(rejoinder.toString())
        when(rejoinder){
            EnumRejoinders.ANSWER_MARKED, EnumRejoinders.ASSENT ->{
                userSaidMarkedIt = true
                furhat.doAsk( session.getUtterance(EnumWordingTypes.CONFIRMATION_REQUEST,rejoinder,EnumFriendliness.UNFRIENDLY) )
            }

            else -> {
                if(userSaidMarkedIt){
                    furhat.doAsk( session.getUtterance(EnumWordingTypes.CONFIRMATION_REQUEST,rejoinder,EnumFriendliness.UNFRIENDLY) )
                }else {
                    furhat.doAsk( session.getUtterance(EnumWordingTypes.CONFIRMATION_REQUEST,rejoinder,EnumFriendliness.FRIENDLY) )
                }
            }
        }
    }
}
