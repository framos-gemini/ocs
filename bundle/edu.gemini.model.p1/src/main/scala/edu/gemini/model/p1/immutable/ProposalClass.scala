package edu.gemini.model.p1.immutable

import edu.gemini.model.p1.{mutable => M}
import java.util.UUID

import edu.gemini.model.p1.immutable.Partners.FtPartner

import scala.collection.JavaConverters._
import scalaz.{-\/, Lens, \/-}

object ProposalClass {

  // Polymorphic deswizzle
  def apply(m:M.ProposalClassChoice):ProposalClass = {
    val q = Option(m.getQueue).map(QueueProposalClass(_))
    val c = Option(m.getClassical).map(ClassicalProposalClass(_))
    val s = Option(m.getSpecial).map(SpecialProposalClass(_))
    val e = Option(m.getExchange).map(ExchangeProposalClass(_))
    val l = Option(m.getLarge).map(LargeProgramClass(_))
    val f = Option(m.getFastTurnaround).map(FastTurnaroundProgramClass(_))
    val i = Option(m.getSip).map(SubaruIntensiveProgramClass(_))
    q.orElse(c).orElse(s).orElse(e).orElse(l).orElse(f).orElse(i).get
  }

  val empty = QueueProposalClass.empty

  // Polymorphic swizzle.
  // Note that we need the outer proposal and a namer to deal with visitor refs for classical proposals
  def mutable(p:Proposal, n:Namer):M.ProposalClassChoice = {
    val m = Factory.createProposalClassChoice
    p.proposalClass match {
      case q: QueueProposalClass          => m.setQueue(q.mutable(p, n))
      case c: ClassicalProposalClass      => m.setClassical(c.mutable(p, n))
      case s: SpecialProposalClass        => m.setSpecial(s.mutable)
      case e: ExchangeProposalClass       => m.setExchange(e.mutable(p, n))
      case l: LargeProgramClass           => m.setLarge(l.mutable)
      case i: SubaruIntensiveProgramClass => m.setSip(i.mutable)
      case l: FastTurnaroundProgramClass  => m.setFastTurnaround(l.mutable(n))
    }
    m
  }

}

sealed trait ProposalClass {

  def itac:Option[Itac]
  def comment:Option[String]
  def key:Option[UUID]

  def reset:ProposalClass

  def requestedTime: TimeAmount
  def minRequestedTime: TimeAmount

  def isSpecial: Boolean

  def classLabel: String

  def multiFacilityGeminiTime: List[GeminiTimeRequired] =
    this match {
      case q: QueueProposalClass     => q.multiFacility.toList.flatMap(_.geminiTimeRequired)
      case l: LargeProgramClass      => l.multiFacility.toList.flatMap(_.geminiTimeRequired)
      case c: ClassicalProposalClass => c.multiFacility.toList.flatMap(_.geminiTimeRequired)
      case _                         => Nil
    }

}

sealed trait GeminiNormalProposalClass extends ProposalClass {
  def subs:Either[List[NgoSubmission], ExchangeSubmission]

  private def anySubs: List[Submission] = subs.left.getOrElse(List(subs.right.get))
  private def sumTimes(f: Submission=>TimeAmount) = Submission.sumTimes(anySubs, f)
  def requestedTime: TimeAmount    = sumTimes(_.request.time)
  def minRequestedTime: TimeAmount = sumTimes(_.request.minTime)
}

object GeminiNormalProposalClass {
  def extractSubs(m: M.GeminiNormalProposalClass): Either[List[NgoSubmission], ExchangeSubmission] = {
    // The part of filter(_.getPartner != null)) was added to handle the case where
    // a partner is gone, specifically the UK. In this way the whole ngo properties is removed
    // This should be removed when we do actual lossy conversions using XSLT
    Option(m.getExchange).map(ExchangeSubmission(_)).toRight(m.getNgo.asScala.toList.filter(_.getPartner != null).map(NgoSubmission(_)))
  }
}

object QueueProposalClass {

  // Lenses
  val tooOption: Lens[QueueProposalClass, ToOChoice] = Lens.lensu((a, b) => a.copy(tooOption = b), _.tooOption)
  val band3request: Lens[QueueProposalClass, Option[SubmissionRequest]] = Lens.lensu((a, b) => a.copy(band3request = b), _.band3request)
  val multiFacility: Lens[QueueProposalClass, Option[MultiFacility]] = Lens.lensu((a, b) => a.copy(multiFacility = b), _.multiFacility)

  def apply(m:M.QueueProposalClass):QueueProposalClass = apply(
    Option(m.getItac).map(Itac(_)),
    Option(m.getComment),
    Option(m.getKey).map(UUID.fromString),
    GeminiNormalProposalClass.extractSubs(m),
    Option(m.getBand3Request).map(SubmissionRequest(_)),
    m.getTooOption,
    Option(m.getMultiFacility).map(MultiFacility(_)),
    m.isJwstSynergy,
    m.isUsLongTerm
  )

  val empty: QueueProposalClass = apply(None, None, None, Left(Nil), None, ToOChoice.None, None, false, false)

}

case class QueueProposalClass(itac: Option[Itac],
                              comment: Option[String],
                              key: Option[UUID],
                              subs: Either[List[NgoSubmission], ExchangeSubmission],
                              band3request: Option[SubmissionRequest],
                              tooOption: ToOChoice,
                              multiFacility: Option[MultiFacility],
                              jwstSynergy: Boolean,
                              usLongTerm: Boolean) extends GeminiNormalProposalClass {

  override val isSpecial = false

  def mutable(p:Proposal, n:Namer):M.QueueProposalClass = {
    val m = Factory.createQueueProposalClass
    m.setItac(itac.map(_.mutable).orNull)
    m.setComment(comment.orNull)
    m.setKey(key.map(_.toString).orNull)
    m.setExchange(subs.right.map(_.mutable(p, n)).right.getOrElse(null))
    m.getNgo.addAll(subs.left.map(lst => lst.map(_.mutable(p, n))).left.getOrElse(Nil).asJava)
    m.setBand3Request(band3request.map(_.mutable).orNull)
    m.setTooOption(tooOption)
    m.setMultiFacility(multiFacility.map(_.mutable).orNull)
    m.setJwstSynergy(jwstSynergy)
    m.setUsLongTerm(usLongTerm)
    m
  }

  def reset: QueueProposalClass = subs match {
    case Left(ss) => copy(key = None, itac = None, subs = Left(ss.map(_.reset)))
    case Right(s) => copy(key = None, itac = None, subs = Right(s.reset))
  }

  def classLabel = "Queue Proposal"

}

object ClassicalProposalClass {
  val multiFacility: Lens[ClassicalProposalClass, Option[MultiFacility]] = Lens.lensu((a, b) => a.copy(multiFacility = b), _.multiFacility)

  def apply(m: M.ClassicalProposalClass): ClassicalProposalClass = apply(
    Option(m.getItac).map(Itac(_)),
    Option(m.getComment),
    Option(m.getKey).map(UUID.fromString),
    GeminiNormalProposalClass.extractSubs(m),
    m.getVisitor.asScala.map(_.getRef).map(Investigator(_).ref).toList,
    Option(m.getMultiFacility).map(MultiFacility(_)),
    m.isJwstSynergy,
    m.isUsLongTerm
  )

  val empty: ClassicalProposalClass = apply(None, None, None, Left(Nil), Nil, None, false, false)

}

/*
 * Note that in the immutable model each Investigator has a visitor bit that indicates whether or not he/she is a
 * classical visitor. So when converting to/from the mutable model we have to do a bit of extra work.
 */
case class ClassicalProposalClass(itac: Option[Itac],
                                  comment: Option[String],
                                  key: Option[UUID],
                                  subs: Either[List[NgoSubmission], ExchangeSubmission],
                                  visitors: List[InvestigatorRef],
                                  multiFacility: Option[MultiFacility],
                                  jwstSynergy: Boolean,
                                  usLongTerm: Boolean)
  extends GeminiNormalProposalClass {

  def mutable(p:Proposal, n:Namer) = {
    val m = Factory.createClassicalProposalClass
    m.setItac(itac.map(_.mutable).orNull)
    m.setComment(comment.orNull)
    m.setKey(key.map(_.toString).orNull)
    m.setExchange(subs.right.map(sub => sub.mutable(p, n)).right.getOrElse(null))
    m.getNgo.addAll(subs.left.map(lst => lst.map(sub => sub.mutable(p, n))).left.getOrElse(Nil).asJava)

    // Write out our visitor list, which we get from the proposal
    m.getVisitor.addAll(visitors.flatMap(_.apply(p)).map {i =>
      val v = Factory.createVisitor
      v.setRef(i.mutable(n))
      v
    }.asJava)
    m.setMultiFacility(multiFacility.map(_.mutable).orNull)
    m.setJwstSynergy(jwstSynergy)
    m.setUsLongTerm(usLongTerm)

    m
  }

  def reset = subs match {
    case Left(ss) => copy(key = None, subs = Left(ss.map(_.reset)))
    case Right(s) => copy(key = None, subs = Right(s.reset))
  }

  def classLabel = "Classical Proposal"

  override val isSpecial = false
}

object SpecialProposalClass {

  // Lens
  val tooOption: Lens[SpecialProposalClass, ToOChoice] = Lens.lensu((a, b) => a.copy(tooOption = b), _.tooOption)
  val sub: Lens[SpecialProposalClass, SpecialSubmission] = Lens.lensu((a, b) => a.copy(sub = b), _.sub)
  val band3request: Lens[SpecialProposalClass, Option[SubmissionRequest]] = Lens.lensu((a, b) => a.copy(band3request = b), _.band3request)

  def apply(m: M.SpecialProposalClass): SpecialProposalClass = apply(
    Option(m.getItac).map(Itac(_)),
    Option(m.getComment),
    Option(m.getKey).map(UUID.fromString),
    SpecialSubmission(m.getSubmission),
    Option(m.getBand3Request).map(SubmissionRequest(_)),
    m.getTooOption,
    m.isJwstSynergy
  )

  val empty: SpecialProposalClass = apply(None, None, None, SpecialSubmission.empty, None, ToOChoice.None, false)

}

case class SpecialProposalClass(itac: Option[Itac],
                                comment: Option[String],
                                key: Option[UUID],
                                sub: SpecialSubmission,
                                band3request: Option[SubmissionRequest],
                                tooOption: ToOChoice,
                                jwstSynergy: Boolean) extends ProposalClass {

  def mutable:M.SpecialProposalClass = {
    val m = Factory.createSpecialProposalClass
    m.setItac(itac.map(_.mutable).orNull)
    m.setComment(comment.orNull)
    m.setKey(key.map(_.toString).orNull)
    m.setSubmission(sub.mutable)
    m.setBand3Request(band3request.map(_.mutable).orNull)
    m.setTooOption(tooOption)
    m.setJwstSynergy(jwstSynergy)
    m
  }

  def reset = copy(key = None, sub = sub.reset)

  def requestedTime: TimeAmount    = sub.request.time
  def minRequestedTime: TimeAmount = sub.request.minTime

  def classLabel = "Special Proposal"

  override val isSpecial = true

}

object ExchangeProposalClass {

  def apply(m:M.ExchangeProposalClass):ExchangeProposalClass = apply(
    Option(m.getItac).map(Itac(_)),
    Option(m.getComment),
    Option(m.getKey).map(UUID.fromString),
    m.getPartner,
    m.getNgo.asScala.map(NgoSubmission.apply).toList)

  def empty = apply(None, None, None, ExchangePartner.SUBARU, Nil)

}
case class ExchangeProposalClass(itac:Option[Itac],
                                 comment:Option[String],
                                 key:Option[UUID],
                                 partner:ExchangePartner,
                                 subs:List[NgoSubmission]) extends ProposalClass {

  def mutable(p:Proposal, n:Namer):M.ExchangeProposalClass = {
    val m = Factory.createExchangeProposalClass
    m.setItac(itac.map(_.mutable).orNull)
    m.setComment(comment.orNull)
    m.setKey(key.map(_.toString).orNull)
    m.setPartner(partner)
    m.getNgo.addAll(subs.map(_.mutable(p, n)).asJava)
    m
  }

  def reset = copy(key = None, subs = subs.map(_.reset))

  def requestedTime: TimeAmount    = Submission.sumTimes(subs, _.request.time)
  def minRequestedTime: TimeAmount = Submission.sumTimes(subs, _.request.minTime)

  def classLabel = "Exchange Proposal"

  override val isSpecial = false

}

final case class GeminiTimeRequired(site: Site, instrument: Instrument, required: Boolean) {
  def mutable: M.GeminiTimeRequired = {
    val m = Factory.createGeminiTimeRequired
    m.setSite(Site.toMutable(site))
    m.setInstrument(instrument.id)
    m.setRequired(required)
    m
  }
}

object GeminiTimeRequired {
  def apply(m: M.GeminiTimeRequired): GeminiTimeRequired = apply(
    Site.fromMutable(m.getSite),
    Instrument.fromMutable(m.getInstrument),
    m.isRequired
  )
}

final case class MultiFacility(geminiTimeRequired: List[GeminiTimeRequired], aeonMode: Boolean) {
  def mutable: M.MultiFacility = {
    val m = Factory.createMultiFacility
    geminiTimeRequired.foreach(g => m.getGeminiTimeRequired.add(g.mutable))
    m.setAeonMode(aeonMode)
    m
  }
}

object MultiFacility {
  val geminiTimeRequired: Lens[MultiFacility, List[GeminiTimeRequired]] = Lens.lensu((a, b) => a.copy(geminiTimeRequired = b), _.geminiTimeRequired)
  val aeonMode: Lens[MultiFacility, Boolean] = Lens.lensu((a, b) => a.copy(aeonMode = b), _.aeonMode)

  def apply(m: M.MultiFacility): MultiFacility = apply(
    m.getGeminiTimeRequired.asScala.map(GeminiTimeRequired(_)).toList,
    m.isAeonMode
  )
}

final case class LargeProgramClass(itac:   Option[Itac],
                            comment:       Option[String],
                            key:           Option[UUID],
                            sub:           LargeProgramSubmission,
                            tooOption:     ToOChoice,
                            multiFacility: Option[MultiFacility],
                            jwstSynergy:   Boolean) extends ProposalClass {

  def mutable:M.LargeProgramClass = {
    val m = Factory.createLargeProgramClass
    m.setItac(itac.map(_.mutable).orNull)
    m.setComment(comment.orNull)
    m.setKey(key.map(_.toString).orNull)
    m.setSubmission(sub.mutable)
    m.setTooOption(tooOption)
    m.setMultiFacility(multiFacility.map(_.mutable).orNull)
    m.setJwstSynergy(jwstSynergy)
    m
  }

  def reset = copy(key = None, sub = sub.reset)

  def requestedTime: TimeAmount    = sub.request.time
  def minRequestedTime: TimeAmount = sub.request.minTime
  def totalLPTime: Option[TimeAmount] = sub.request.totalLPTime

  def classLabel = "Large Program"

  override val isSpecial = false

}

object LargeProgramClass {

  // Lens
  val tooOption: Lens[LargeProgramClass, ToOChoice] = Lens.lensu((a, b) => a.copy(tooOption = b), _.tooOption)
  val sub: Lens[LargeProgramClass,LargeProgramSubmission] = Lens.lensu((a, b) => a.copy(sub = b), _.sub)
  val multiFacility: Lens[LargeProgramClass, Option[MultiFacility]] = Lens.lensu((a, b) => a.copy(multiFacility = b), _.multiFacility)

  def apply(m: M.LargeProgramClass): LargeProgramClass = apply(
    Option(m.getItac).map(Itac(_)),
    Option(m.getComment),
    Option(m.getKey).map(UUID.fromString),
    LargeProgramSubmission(m.getSubmission),
    m.getTooOption,
    Option(m.getMultiFacility).map(MultiFacility(_)),
    m.isJwstSynergy
  )

  def empty: LargeProgramClass = apply(None, None, None, LargeProgramSubmission.empty, ToOChoice.None, None, false)

}

case class SubaruIntensiveProgramClass(itac: Option[Itac],
                            comment:         Option[String],
                            key:             Option[UUID],
                            tooOption:       ToOChoice,
                            sub:             SubaruIntensiveProgramSubmission)
  extends ProposalClass {

  def mutable: M.SubaruIntensiveProgramClass = {
    val m = Factory.createSubaruIntensiveProgramClass
    m.setItac(itac.map(_.mutable).orNull)
    m.setComment(comment.orNull)
    m.setKey(key.map(_.toString).orNull)
    m.setSubmission(sub.mutable)
    m.setTooOption(tooOption)
    m
  }

  def reset = copy(key = None, sub = sub.reset)

  def requestedTime: TimeAmount    = sub.request.time
  def minRequestedTime: TimeAmount = sub.request.minTime
  def totalLPTime: Option[TimeAmount] = sub.request.totalLPTime

  def classLabel = "Subaru Intensive Program"

  override val isSpecial = false

}

object SubaruIntensiveProgramClass {

  // Lens
  val tooOption: Lens[SubaruIntensiveProgramClass, ToOChoice] = Lens.lensu((a, b) => a.copy(tooOption = b), _.tooOption)
  val sub: Lens[SubaruIntensiveProgramClass, SubaruIntensiveProgramSubmission] = Lens.lensu((a, b) => a.copy(sub = b), _.sub)

  def apply(m:M.SubaruIntensiveProgramClass): SubaruIntensiveProgramClass = apply(
    Option(m.getItac).map(Itac(_)),
    Option(m.getComment),
    Option(m.getKey).map(UUID.fromString),
    m.getTooOption,
    SubaruIntensiveProgramSubmission(m.getSubmission))

  def empty = apply(None, None, None, ToOChoice.None, SubaruIntensiveProgramSubmission.empty)

}

case class FastTurnaroundProgramClass(itac:               Option[Itac],
                                      comment:            Option[String],
                                      key:                Option[UUID],
                                      sub:                FastTurnaroundSubmission,
                                      band3request:       Option[SubmissionRequest],
                                      tooOption:          ToOChoice,
                                      reviewer:           Option[Investigator],
                                      mentor:             Option[Investigator],
                                      partnerAffiliation: FtPartner,
                                      jwstSynergy:        Boolean) extends ProposalClass {
  def mutable(n: Namer):M.FastTurnaroundProgramClass = {
    val m = Factory.createFastTurnaroundProgramClass
    m.setItac(itac.map(_.mutable).orNull)
    m.setComment(comment.orNull)
    m.setKey(key.map(_.toString).orNull)
    m.setTooOption(tooOption)
    m.setBand3Request(band3request.map(_.mutable).orNull)
    m.setSubmission(sub.mutable)
    m.setReviewer(reviewer.map(_.mutable(n)).orNull)
    m.setMentor(mentor.map(_.mutable(n)).orNull)
    partnerAffiliation match {
      case Some(-\/(p)) =>
        m.setPartnerAffiliation(p)
        m.setExchangeAffiliation(null)
      case Some(\/-(e)) =>
        m.setPartnerAffiliation(null)
        m.setExchangeAffiliation(e)
      case _            =>
        m.setPartnerAffiliation(null)
        m.setExchangeAffiliation(null)
    }
    m.setJwstSynergy(jwstSynergy)
    m
  }

  def reset = copy(key = None, sub = sub.reset)

  def requestedTime: TimeAmount    = sub.request.time
  def minRequestedTime: TimeAmount = sub.request.minTime

  def classLabel = "Fast-turnaround Program"

  override val isSpecial = true

}

object FastTurnaroundProgramClass {

  // Lens
  val tooOption:Lens[FastTurnaroundProgramClass, ToOChoice] = Lens.lensu((a, b) => a.copy(tooOption = b), _.tooOption)
  val sub:Lens[FastTurnaroundProgramClass ,FastTurnaroundSubmission] = Lens.lensu((a, b) => a.copy(sub = b), _.sub)
  val band3request:Lens[FastTurnaroundProgramClass, Option[SubmissionRequest]] = Lens.lensu((a, b) => a.copy(band3request = b), _.band3request)
  val reviewer:Lens[FastTurnaroundProgramClass, Option[Investigator]] = Lens.lensu((a, b) => a.copy(reviewer = b), _.reviewer)
  val reviewerAndMentor:Lens[FastTurnaroundProgramClass, (Option[Investigator], Option[Investigator])] = Lens.lensu((a, b) => a.copy(reviewer = b._1, mentor = b._2), f => (f.reviewer, f.mentor))
  val mentor:Lens[FastTurnaroundProgramClass, Option[Investigator]] = Lens.lensu((a, b) => a.copy(mentor = b), _.mentor)
  val affiliation:Lens[FastTurnaroundProgramClass, FtPartner] = Lens.lensu((a, b) => a.copy(partnerAffiliation = b), _.partnerAffiliation)

  def affiliation(m: M.FastTurnaroundProgramClass): FtPartner = {
    (Option(m.getPartnerAffiliation), Option(m.getExchangeAffiliation)) match {
      case (Some(p), _)                      => Some(-\/(p))
      case (_, Some(ExchangePartner.SUBARU)) => Some(\/-(ExchangePartner.SUBARU))
      case _                                 => None
    }
  }

  def apply(m:M.FastTurnaroundProgramClass):FastTurnaroundProgramClass = apply(
    Option(m.getItac).map(Itac(_)),
    Option(m.getComment),
    Option(m.getKey).map(UUID.fromString),
    FastTurnaroundSubmission(m.getSubmission),
    Option(m.getBand3Request).map(SubmissionRequest(_)),
    m.getTooOption,
    Option(m.getReviewer).map(Investigator.apply),
    Option(m.getMentor).map(Investigator.apply),
    affiliation(m),
    m.isJwstSynergy
  )

  def empty: FastTurnaroundProgramClass = apply(None, None, None, FastTurnaroundSubmission.empty, None, ToOChoice.None, None, None, None, false)

}
