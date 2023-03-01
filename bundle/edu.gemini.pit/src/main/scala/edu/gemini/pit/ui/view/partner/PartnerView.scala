package edu.gemini.pit.ui.view.partner

import edu.gemini.pit.model.Model
import edu.gemini.pit.ui.util.gface.SimpleListViewer
import edu.gemini.shared.gui.textComponent.TextRenderer

import swing.BorderPanel.Position._
import swing.event.SelectionChanged
import swing._
import Swing._
import scalaz._
import Scalaz._
import edu.gemini.pit.ui.editor.{GeminiTimeRequiredEditor, Institutions, LargeSubmissionRequestEditor, SubmissionRequestEditor, VisitorSelector}
import edu.gemini.model.p1.immutable._
import edu.gemini.model.p1.immutable.Partners._

import java.awt.Color
import edu.gemini.pit.ui.util._
import edu.gemini.pit.ui.binding._

import javax.swing.{BorderFactory, Icon, JComboBox, JLabel}
import javax.swing.{ComboBoxModel, DefaultComboBoxModel, SwingConstants}
import edu.gemini.model.p1.immutable.Partners.FtPartner

// An enum for multi facility selection
object MultiFacilitySelection extends Enumeration {
  val Yes = Value("Yes")
  val No  = Value("No")
  type MultiFacilitySelection = Value
}

object GeminiRequired extends Enumeration {
  val Yes = Value("Yes")
  val No  = Value("No")
  type GeminiRequired = Value
}

// This is the proposal class editor, and it's actually kind of involved.
class PartnerView extends BorderPanel with BoundView[Proposal] {view =>
  implicit val boolMonoid = Monoid.instance[Boolean](_ || _,  false)

  // Bound
  val lens = Model.proposal
  override def children = List(header, list, footer)

  // We have local state that allows us to go back to the previous proposal class. This is really just a convenience
  // for the user so you can change proposal class and not lose what you had entered earlier. It's all forgotten when
  // you exit the app.
  var localQueue = QueueProposalClass.empty
  var localClassical = ClassicalProposalClass.empty
  var localExchange = ExchangeProposalClass.empty
  var localSpecial = SpecialProposalClass.empty
  var localLarge = LargeProgramClass.empty
  var localSip = SubaruIntensiveProgramClass.empty
  var localFast = FastTurnaroundProgramClass.empty

  // We update our local state whenever we refresh.
  override def refresh(m:Option[Proposal]): Unit = {
    m.map(_.proposalClass).foreach {
      case q: QueueProposalClass          => localQueue = q
      case c: ClassicalProposalClass      => localClassical = c
      case e: ExchangeProposalClass       => localExchange = e
      case s: SpecialProposalClass        => localSpecial = s
      case l: LargeProgramClass           => localLarge = l
      case i: SubaruIntensiveProgramClass => localSip = i
      case f: FastTurnaroundProgramClass  => localFast = f
    }
  }

  // An enum for proposal class selections
  object ProposalClassSelection extends Enumeration {
    val Large     = Value("Large Program Observing at Gemini")
    val Fast      = Value("Fast Turnaround Observing at Gemini")
    val Queue     = Value("Queue Observing at Gemini")
    val Classical = Value("Classical Observing at Gemini")
    val Exchange  = Value("Exchange Observing at Keck/Subaru")
    val SIP       = Value("Intensive Program Observing at Subaru")
    val Special   = Value("Other Proposal Types")
  }

  // An enum for submission type, so we can filter the partner/exchange list
  object PartnerType extends Enumeration {
    val GeminiPartner  = Value("Gemini Partner Request")
    val ExchangeCFH    = Value("Exchange Request (CFH PIs)")
    val ExchangeKeck   = Value("Exchange Request (Keck PIs)")
    val ExchangeSubaru = Value("Exchange Request (Japanese PIs)")
  }

  // An enum for Band 3 options
  object Band3Option extends Enumeration {
    val Band12       = Value("No")
    val Band123      = Value("Yes")
    type Band3Option = Value
  }

  // An enum for JWST Synergy
  object JWSTSynergyOption extends Enumeration {
    val Yes = Value("Yes")
    val No  = Value("No")
    type JWSTSynergyOption = Value
  }

  // An enum for US Long Term
  object USLongTermOption extends Enumeration {
    val Yes = Value("Yes")
    val No  = Value("No")
    type USLongTermOption = Value
  }

  // Bring the above enums into scope

  import ProposalClassSelection._
  import PartnerType._
  import Band3Option._

  // Our content, defined below
  add(header, North)
  add(list, Center)
  add(footer, South)

  // Our footer, up here because it's the last one I did
  object footer extends Label with Bound.Self[Proposal] {
    horizontalAlignment = Alignment.Left
    opaque = true
    foreground = Color.DARK_GRAY
    background = new Color(255, 255, 224)
    border = BorderFactory.createCompoundBorder(
      (new StdToolbar).border,
      BorderFactory.createEmptyBorder(2, 4, 2, 4))
    override def refresh(m:Option[Proposal]): Unit = {

      val (reqTime, minTime) = m.map(_.proposalClass).map(r => (r.requestedTime.hours, r.minRequestedTime.hours)) | ((0.0, 0.0))

      val b3 = m.flatMap(_.proposalClass match {
        case q: QueueProposalClass => q.band3request.map(r => (r.time.hours, r.minTime.hours))
        case s: SpecialProposalClass if s.sub.specialType == SpecialProposalType.GUARANTEED_TIME =>
          s.band3request.map(r => (r.time.hours, r.minTime.hours))
        case _                     => None
      })

      text = b3.map {
        case (reqTimeB3, minTimeB3) =>
          f"Total request: $reqTime%3.2f hr ($minTime%3.2f hr min) | Band 3 request: $reqTimeB3%3.2f hr ($minTimeB3%3.2f hr min)"
      }.getOrElse {
        ~m.map(_.proposalClass match {
          case l:LargeProgramClass  =>
            val totalLPTime = l.totalLPTime.getOrElse(TimeAmount.empty).hours
            f"Total request: $reqTime%3.2f hr ($minTime%3.2f hr min) | Total LP time $totalLPTime%3.2f"
          case _                    =>
            f"Total request: $reqTime%3.2f hr ($minTime%3.2f hr min)"
        })
      }

    }
  }

  // Our header, which is basically everything except the partner/exchange list
  object header extends GridBagPanel with Rows with Bound.Self[Proposal] {

    // We have a bunch of children
    override def children = List(
      proposalClass,
      multiFacilityLabel, multiFacilityPanel,
      jwstSynergyLabel, jwstSynergyPanel,
      usLongTermLabel, usLongTermPanel,
      band3Label, band3,
      tooLabel, tooOption,
      visitorsLabel, visitors,
      ftReviewerLabel, reviewer,
      ftMentorLabel, mentor,
      partnerAffiliationLabel, partnerAffiliation,
      specialLabel, special,
      specialTimeLabel, specialTime,
      partnerTypeLabel, partnerType,
      siteLabel, siteCombo
    )

    // The proposal class row is always visible
    addRow(new Label("Proposal Class:"), proposalClass)

    // Site selection, for exchange observing
    addRow(siteLabel, siteCombo)

    // Visible only in Queue mode
    addRow(band3Label, band3)
    addRow(tooLabel, tooOption)

    addRow(multiFacilityLabel, multiFacilityPanel)
    addRow(jwstSynergyLabel, jwstSynergyPanel)
    addRow(usLongTermLabel, usLongTermPanel)

    // Visible only in Classical mode
    addRow(visitorsLabel, visitors)

    // Visible only in Special mode
    addRow(specialLabel, special)
    addRow(specialTimeLabel, specialTime)

    // Visible in Queue and Classical mode, limited in Exchange mode
    addRow(partnerTypeLabel, partnerType)

    // Visible only in FT mode
    addRow(ftReviewerLabel, reviewer)
    // Visible only in FT mode
    addRow(ftMentorLabel, mentor)
    // Visible only in FT mode
    addRow(partnerAffiliationLabel, partnerAffiliation)

    // Site label
    lazy val siteLabel = dvLabel("Observatory:") {
      case _: ExchangeProposalClass => true
    }

    // Site combo
    object siteCombo extends ComboBox(ExchangePartner.values.filterNot(_ == ExchangePartner.CFH)) with Bound[Proposal, ProposalClass] with TextRenderer[ExchangePartner] {

      val lens = Proposal.proposalClass

      selection.reactions += {
        case SelectionChanged(_) => model match {
          case Some(e: ExchangeProposalClass) => model = Some(e.copy(partner = selection.item))
          case _                              => // shouldn't happen
        }
      }

      override def refresh(m: Option[ProposalClass]): Unit = {
        enabled = canEdit
        m.foreach {
          case e: ExchangeProposalClass       =>
            selection.item = e.partner
            visible = true
          case _                              =>
            visible = false
        }
      }

      def text(p: ExchangePartner): String = Partners.name(p)
    }

    // The proposal class combo is always visible
    object proposalClass extends ComboBox(ProposalClassSelection.values.toSeq) with Bound.Self[Proposal] {

      selection.reactions += {
        case SelectionChanged(_) =>

          // [UX-1113] Use the same partner time requests when switching between queue/classical proposals
          model.map(_.proposalClass).foreach {

            // Q <=> C (all cases)
            case q: QueueProposalClass if selection.item == Classical =>
              localClassical = localClassical.copy(subs = q.subs, multiFacility = q.multiFacility.map(_.copy(aeonMode = false)))
            case c: ClassicalProposalClass if selection.item == Queue => localQueue = localQueue.copy(subs = c.subs, multiFacility = c.multiFacility.map(_.copy(aeonMode = true)))

            // Q <=> L (all cases)
            case q: QueueProposalClass if selection.item == Large =>
              localLarge = localLarge.copy(multiFacility = q.multiFacility.map(_.copy(aeonMode = true)))

            case l: LargeProgramClass if selection.item == Queue =>
              localQueue = localQueue.copy(multiFacility = l.multiFacility.map(_.copy(aeonMode = true)))

            // C <=> L (all cases)
            case c: ClassicalProposalClass if selection.item == Large =>
              localLarge = localLarge.copy(multiFacility = c.multiFacility.map(_.copy(aeonMode = true)))

            case l: LargeProgramClass if selection.item == Classical =>
              localClassical = localClassical.copy(multiFacility = l.multiFacility.map(_.copy(aeonMode = false)))
            // {Q,C} => E when Q/C is NGO
            case g: GeminiNormalProposalClass if selection.item == Exchange => g.subs match {
              case Left(ns) => localExchange = localExchange.copy(subs = ns)
              case _        => // nop
            }

            // E => {Q,C} when Q/C is NGO
            case e: ExchangeProposalClass if selection.item == Classical && localClassical.subs.isLeft => localClassical = localClassical.copy(subs = Left(e.subs))
            case e: ExchangeProposalClass if selection.item == Queue && localQueue.subs.isLeft => localQueue = localQueue.copy(subs = Left(e.subs))

            case _ if selection.item == Fast => model = model.map(m =>m .copy(meta = m.meta.copy(secondAttachment = none)))

            case _ => // nop
          }

          selection.item match {
            case Queue     => model = model.map(_.copy(proposalClass = localQueue))
            case Classical => model = model.map(_.copy(proposalClass = localClassical))
            case Exchange  => model = model.map(_.copy(proposalClass = localExchange))
            case Special   => model = model.map(_.copy(proposalClass = localSpecial))
            case Large     => model = model.map(_.copy(proposalClass = localLarge))
            case SIP       => model = model.map(_.copy(proposalClass = localSip))
            case Fast      => model = model.map(_.copy(proposalClass = localFast))
          }

      }

      override def refresh(m: Option[Proposal]): Unit = {
        enabled = canEdit
        selection.item = m.map(_.proposalClass).map {
          case _: QueueProposalClass          => Queue
          case _: ClassicalProposalClass      => Classical
          case _: ExchangeProposalClass       => Exchange
          case _: SpecialProposalClass        => Special
          case _: LargeProgramClass           => Large
          case _: SubaruIntensiveProgramClass => SIP
          case _: FastTurnaroundProgramClass  => Fast
        }.getOrElse(Queue)
      }
    }

    // TOO label and combo box
    lazy val tooLabel = dvLabel("ToO Activation:") {
      case _: QueueProposalClass          => true
      case _: LargeProgramClass           => true
      case _: FastTurnaroundProgramClass  => true
      case _: SubaruIntensiveProgramClass => true
      case _: SpecialProposalClass        => true
    }

    // TOO option combo box
    object tooOption extends ComboBox(ToOChoice.values.toSeq) with ValueRenderer[ToOChoice] with Bound[Proposal, ProposalClass] {

      val lens = Proposal.proposalClass

      override def refresh(m: Option[ProposalClass]): Unit = {
        enabled = canEdit
        m.foreach {
          case q: QueueProposalClass          =>
            selection.item = q.tooOption
            visible = true
          case l: LargeProgramClass           =>
            selection.item = l.tooOption
            visible = true
          case f: FastTurnaroundProgramClass  =>
            selection.item = f.tooOption
            visible = true
          case s: SubaruIntensiveProgramClass =>
            selection.item = s.tooOption
            visible = true
          case s: SpecialProposalClass =>
            selection.item = s.tooOption
            visible = true
          case _                              =>
            visible = false
        }
      }

      selection.reactions += {
        case SelectionChanged(_) => model match {
          case Some(q: QueueProposalClass)          => model = Some(QueueProposalClass.tooOption.set(q, selection.item))
          case Some(l: LargeProgramClass)           => model = Some(LargeProgramClass.tooOption.set(l, selection.item))
          case Some(f: FastTurnaroundProgramClass)  => model = Some(FastTurnaroundProgramClass.tooOption.set(f, selection.item))
          case Some(s: SubaruIntensiveProgramClass) => model = Some(SubaruIntensiveProgramClass.tooOption.set(s, selection.item))
          case Some(s: SpecialProposalClass)        => model = Some(SpecialProposalClass.tooOption.set(s, selection.item))
          case _                                    => // shouldn't happen
        }
      }

    }

    // Multi label and combo box
    lazy val multiFacilityLabel = dvLabel("AEON/Multi-facility:") {
      case _: QueueProposalClass     => true
      case _: LargeProgramClass      => true
      case _: ClassicalProposalClass => true
    }

    object multiFacilityPanel extends FlowPanel(FlowPanel.Alignment.Left)() with Bound.Self[Proposal] {

      // Configure the panel
      vGap = 0
      hGap = 0

      override def children = List(multiFacility, geminiTimeRequiredButton)

      peer.add(multiFacility.peer)
      peer.add(geminiTimeRequiredButton.peer)

      override def refresh(m: Option[Proposal]): Unit = {
        enabled = canEdit
        m.map(_.proposalClass).foreach {
          case q: QueueProposalClass          =>
            visible = true
            geminiTimeRequiredButton.visible = q.multiFacility.exists(_.aeonMode)
          case l: LargeProgramClass           =>
            visible = true
            geminiTimeRequiredButton.enabled = l.multiFacility.exists(_.aeonMode)
          case c: ClassicalProposalClass      =>
            visible = true
            geminiTimeRequiredButton.enabled = c.multiFacility.exists(_.aeonMode)
          case _                              =>
            visible = false
        }
      }

      object multiFacility extends ComboBox(MultiFacilitySelection.values.toSeq) with Bound.Self[Proposal] {
        comboBox =>
        override def refresh(m: Option[Proposal]): Unit = {
          enabled = canEdit
          m.map(_.proposalClass).foreach {
            case q: QueueProposalClass          =>
              selection.item = q.multiFacility.fold(MultiFacilitySelection.No)(_ => MultiFacilitySelection.Yes)
              visible = true
            case l: LargeProgramClass           =>
              selection.item = l.multiFacility.fold(MultiFacilitySelection.No)(_ => MultiFacilitySelection.Yes)
              visible = true
            case c: ClassicalProposalClass      =>
              selection.item = c.multiFacility.fold(MultiFacilitySelection.No)(_ => MultiFacilitySelection.Yes)
              visible = true
            case _                              =>
              visible = false
          }
        }

        renderer = new ListView.Renderer[MultiFacilitySelection.Value] {
          private val delegate = renderer

          def componentFor(list: ListView[_ <: MultiFacilitySelection.Value], isSelected: Boolean, focused: Boolean, a: MultiFacilitySelection.Value, index: Int): Component = {
            val c = delegate.componentFor(list, isSelected, focused, a, index)
            val t = Option(a).map(_.toString).getOrElse("Select")
            c.peer.asInstanceOf[JLabel].setText(t)
            c
          }
        }

        def defaultMf: List[GeminiTimeRequired] = ~model.map(_.defaultMultiFacilityGeminiTime)

        selection.reactions += {
          case SelectionChanged(_) => model = model.map(_.proposalClass).flatMap {
            case q: QueueProposalClass          =>
              model.map(_.copy(proposalClass = selection.item match {
                case MultiFacilitySelection.Yes =>
                  QueueProposalClass.multiFacility.mod(_.orElse(Some(new MultiFacility(defaultMf, true))), q)
                case MultiFacilitySelection.No  =>
                  QueueProposalClass.multiFacility.set(q, None)
                case _                          => q
              }))
            case l: LargeProgramClass           =>
              model.map(_.copy(proposalClass = selection.item match {
                case MultiFacilitySelection.Yes =>
                  LargeProgramClass.multiFacility.mod(_.orElse(Some(new MultiFacility(defaultMf, true))), l)
                case MultiFacilitySelection.No  =>
                  LargeProgramClass.multiFacility.set(l, None)
                case _                          => l
              }))
            case c: ClassicalProposalClass      =>
              model.map(_.copy(proposalClass = selection.item match {
                case MultiFacilitySelection.Yes =>
                  ClassicalProposalClass.multiFacility.mod(_.orElse(Some(new MultiFacility(defaultMf, false))), c)
                case MultiFacilitySelection.No  =>
                  ClassicalProposalClass.multiFacility.set(c, None)
                case _                          => c
              }))
            case _                                    =>
              model
          }
        }
      }

      object geminiTimeRequiredButton extends Button with Bound.Self[Proposal] {
        button =>

        // On refresh, sync the observation resources with the gemini required list
        override def refresh(m: Option[Proposal]): Unit = {
          enabled = canEdit

          val existing = m.toList.flatMap(_.multiFacilityGeminiTime)

          val onObservations = m.toList.flatMap(_.defaultMultiFacilityGeminiTime)

          val toAdd = m.toList.flatMap(_.defaultMultiFacilityGeminiTime.filterNot { d =>
            existing.find(t => t.site == d.site && t.instrument == d.instrument).isDefined
          })

          val toRemove = (existing.filterNot { d =>
            m.toList.flatMap(_.defaultMultiFacilityGeminiTime).find(t => t.site == d.site && t.instrument == d.instrument).isDefined
          })

          val mod = Functor[Option].lift((x: MultiFacility) => x.copy(geminiTimeRequired = toAdd ::: x.geminiTimeRequired.filterNot(toRemove.contains)))
          val newPC = (model.map(_.proposalClass).map {
              case q: QueueProposalClass =>
                QueueProposalClass.multiFacility.mod(mod, q)
              case l: LargeProgramClass =>
                LargeProgramClass.multiFacility.mod(mod, l)
              case c: ClassicalProposalClass =>
                ClassicalProposalClass.multiFacility.mod(mod, c)
              case x => x
            })
          newPC.foreach(pc => model = model.map(_.copy(proposalClass = pc)))
        }

        def pairTime(tr: List[GeminiTimeRequired]): List[(Option[TimeAmount], GeminiTimeRequired)] =
          tr.map { t =>
            (model.flatMap(_.timePerInstrument.get((t.site, t.instrument))), t)
          }

        def qp  = for {
            s @ QueueProposalClass(_, _, _, _, _, _,Some(mf), _, _) <- model.map(_.proposalClass)
            l <- GeminiTimeRequiredEditor.open(pairTime(mf.geminiTimeRequired), button)
          } yield QueueProposalClass.multiFacility.set(s, mf.copy(geminiTimeRequired = l).some)

        def lp = for {
            s @ LargeProgramClass(_, _, _, _, _,Some(mf), _) <- model.map(_.proposalClass)
            l <- GeminiTimeRequiredEditor.open(pairTime(mf.geminiTimeRequired), button)
          } yield LargeProgramClass.multiFacility.set(s, mf.copy(geminiTimeRequired = l).some)

        def cp = for {
            s @ ClassicalProposalClass(_, _, _, _, _,Some(mf), _, _) <- model.map(_.proposalClass)
            l <- GeminiTimeRequiredEditor.open(pairTime(mf.geminiTimeRequired), button)
          } yield ClassicalProposalClass.multiFacility.set(s, mf.copy(geminiTimeRequired = l).some)
        action = Action("") {
          qp.orElse(lp).orElse(cp).foreach(r => model = model.map(_.copy(proposalClass = r)))
        }

        icon = SharedIcons.ICON_VARIANT

      }
    }

    lazy val geminiRequiredTimeLabel = dvLabel("Gemini Time Required:") {
      case q: QueueProposalClass     => q.multiFacility.isDefined
      case l: LargeProgramClass      => l.multiFacility.isDefined
      case c: ClassicalProposalClass => c.multiFacility.isDefined
    }

    lazy val jwstSynergyLabel = dvLabel("JWST Synergy:") {
      case _: QueueProposalClass                                                                            => true
      case _: LargeProgramClass                                                                             => true
      case _: ClassicalProposalClass                                                                        => true
    }

    object jwstSynergyPanel extends FlowPanel(FlowPanel.Alignment.Left)() with Bound.Self[Proposal] {

      // Configure the panel
      vGap = 0
      hGap = 0

      override def children = List(jwstSynergyCombo)

      peer.add(jwstSynergyCombo.peer)

      object jwstSynergyCombo extends ComboBox(JWSTSynergyOption.values.toSeq) with Bound[Proposal, ProposalClass] {
        // A lens to allow us to set the type in one shot
        val lens = Proposal.proposalClass

        override def refresh(m: Option[ProposalClass]): Unit = {
          enabled = canEdit
          m.foreach {
            case q: QueueProposalClass                                                                                      =>
              visible = true
              selection.item = if (q.jwstSynergy) JWSTSynergyOption.Yes else JWSTSynergyOption.No
            case l: LargeProgramClass                                                                                       =>
              visible = true
              selection.item = if (l.jwstSynergy) JWSTSynergyOption.Yes else JWSTSynergyOption.No
            case c: ClassicalProposalClass                                                                                  =>
              visible = true
              selection.item = if (c.jwstSynergy) JWSTSynergyOption.Yes else JWSTSynergyOption.No
            case _                                                                                                          =>
              visible = false
          }
        }

        selection.reactions += {
          case SelectionChanged(_) => model  = model match {
            case Some(q: QueueProposalClass) =>
              q.copy(jwstSynergy = selection.item == JWSTSynergyOption.Yes).some
            case Some(l: LargeProgramClass) =>
              l.copy(jwstSynergy = selection.item == JWSTSynergyOption.Yes).some
            case Some(c: ClassicalProposalClass) =>
              c.copy(jwstSynergy = selection.item == JWSTSynergyOption.Yes).some
            case Some(f: FastTurnaroundProgramClass) =>
              f.copy(jwstSynergy = selection.item == JWSTSynergyOption.Yes).some
            case Some(s: SpecialProposalClass) =>
              s.copy(jwstSynergy = selection.item == JWSTSynergyOption.Yes).some
            case x => x
          }
        }

      }
    }

    lazy val usLongTermLabel = dvLabel("US Long Term:") {
      case _: QueueProposalClass                                                                            => true
      case _: ClassicalProposalClass                                                                        => true
    }

    object usLongTermPanel extends FlowPanel(FlowPanel.Alignment.Left)() with Bound.Self[Proposal] {

      // Configure the panel
      vGap = 0
      hGap = 0

      override def children = List(usLongTermCombo)

      peer.add(usLongTermCombo.peer)

      object usLongTermCombo extends ComboBox(USLongTermOption.values.toSeq) with Bound[Proposal, ProposalClass] {
        // A lens to allow us to set the type in one shot
        val lens = Proposal.proposalClass

        override def refresh(m: Option[ProposalClass]): Unit = {
          enabled = canEdit
          m.foreach {
            case q: QueueProposalClass                                                                                      =>
              visible = true
              selection.item = if (q.usLongTerm) USLongTermOption.Yes else USLongTermOption.No
            case c: ClassicalProposalClass                                                                                  =>
              visible = true
              selection.item = if (c.usLongTerm) USLongTermOption.Yes else USLongTermOption.No
            case _                                                                                                          =>
              visible = false
          }
        }

        selection.reactions += {
          case SelectionChanged(_) => model  = model match {
            case Some(q: QueueProposalClass) =>
              q.copy(usLongTerm = selection.item == USLongTermOption.Yes).some
            case Some(c: ClassicalProposalClass) =>
              c.copy(usLongTerm = selection.item == USLongTermOption.Yes).some
            case x => x
          }
        }

      }
    }

    // Band 3 Label
    lazy val band3Label = dvLabel("Consider for Band 3:") {
      case _: QueueProposalClass   => true
      case s: SpecialProposalClass => s.sub.specialType == SpecialProposalType.GUARANTEED_TIME
      // case _: FastTurnaroundProgramClass => true // REL-1896
    }

    // Band 3 control set
    object band3 extends FlowPanel(FlowPanel.Alignment.Left)() with Bound.Self[Proposal] {

      // Bound
      override def children = List(option, edit, label)

      // We have local state as well, same as above, so we don't lose edits
      var localRequest = SubmissionRequest.empty

      // Configure the panel
      vGap = 0
      hGap = 0

      // Add our children, defined below
      peer.add(option.peer)
      peer.add(edit.peer)
      peer.add(label.peer)

      // On refresh we update visibility and local state
      override def refresh(m: Option[Proposal]): Unit = {
        visible = ~m.map(_.proposalClass).map {
          case _: QueueProposalClass         => true
          case s: SpecialProposalClass       => s.sub.specialType == SpecialProposalType.GUARANTEED_TIME
          //case _: FastTurnaroundProgramClass => true // REL-1896 Hide it but keep the code
          case _                             => false
        }
        m.map(_.proposalClass).foreach {
          case q: QueueProposalClass         => q.band3request.foreach(r => localRequest = r)
          case f: FastTurnaroundProgramClass => f.band3request.foreach(r => localRequest = r)
          case s: SpecialProposalClass       => s.band3request.foreach(r => localRequest = r)
          case _                             => // ignore
        }
      }

      // Band 3 option combo
      object option extends ComboBox(Band3Option.values.toSeq) with Bound.Self[Proposal] {

        preferredSize = (75, preferredSize.height)

        override def refresh(m: Option[Proposal]): Unit = {
          enabled = canEdit
          selection.item = m.map { p =>
            val a = p.proposalClass match {
              case q: QueueProposalClass         => q.band3request.map(_ => Band123).getOrElse(Band12)
              case f: FastTurnaroundProgramClass => f.band3request.map(_ => Band123).getOrElse(Band12)
              case s: SpecialProposalClass       => s.band3request.map(_ => Band123).getOrElse(Band12)
              case _                             => Band12
            }
            if (a == Band12 && !p.meta.band3OptionChosen) null else a
          }.orNull
        }

        renderer = new ListView.Renderer[Band3Option] {
          val delegate = renderer

          def componentFor(list: ListView[_ <: Band3Option], isSelected: Boolean, focused: Boolean, a: Band3Option, index: Int) = {
            val c = delegate.componentFor(list, isSelected, focused, a, index)
            val t = Option(a).map(_.toString).getOrElse("Select")
            c.peer.asInstanceOf[JLabel].setText(t)
            c
          }
        }

        selection.reactions += {
          case SelectionChanged(_) =>
            for {
              p <- model
              sel <- Option(selection.item) // could be null
              pc <- p.proposalClass match {
                case q: QueueProposalClass         => sel match {
                  case Band12  => Some(QueueProposalClass.band3request.set(q, None))
                  case Band123 => Some(QueueProposalClass.band3request.set(q, Some(localRequest)))
                }
                case f: FastTurnaroundProgramClass => sel match {
                  case Band12  => Some(FastTurnaroundProgramClass.band3request.set(f, None))
                  case Band123 => Some(FastTurnaroundProgramClass.band3request.set(f, Some(localRequest)))
                }
                case s: SpecialProposalClass => sel match {
                  case Band12  => Some(SpecialProposalClass.band3request.set(s, None))
                  case Band123 => Some(SpecialProposalClass.band3request.set(s, Some(localRequest)))
                }
                case _                             => None
              }
            } {

              val p0 = (Proposal.meta andThen Meta.band3OptionChosen).set(p, true)
              val p1 = Proposal.proposalClass.set(p0, pc)

              model = Some(p1)
            }
        }

      }

      // Band 3 time edit button
      object edit extends Button with Bound[Proposal, ProposalClass] {
        button =>

        // A lens to allow us to set the type in one shot
        val lens = Proposal.proposalClass

        // On refresh, set our visible state
        override def refresh(m: Option[ProposalClass]): Unit = {
          enabled = canEdit
          visible = ~m.map {
            case q: QueueProposalClass         => q.band3request.isDefined
            case s: SpecialProposalClass       => s.band3request.isDefined
            // case f: FastTurnaroundProgramClass => f.band3request.isDefined //REL-1896
            case _                             => false
          }
        }

        def queueEditor: Option[QueueProposalClass] = for {
          q @ QueueProposalClass(_, _, _, _, Some(r), _, _, _, _) <- model
          (r, _, _) <- SubmissionRequestEditor.open(r, None, Nil, None, button)
        } yield QueueProposalClass.band3request.set(q, Some(r))

        def ftEditor: Option[FastTurnaroundProgramClass] = for {
          ft @ FastTurnaroundProgramClass(_, _, _, _, Some(r), _, _, _, _, _) <- model
          (r, _, _) <- SubmissionRequestEditor.open(r, None, Nil, None, button)
        } yield FastTurnaroundProgramClass.band3request.set(ft, Some(r))


        def stEditor: Option[SpecialProposalClass] = for {
          sp @ SpecialProposalClass(_, _, _, _, Some(r), _, _) <- model
          (r, _, _) <- SubmissionRequestEditor.open(r, None, Nil, None, button)
        } yield SpecialProposalClass.band3request.set(sp, Some(r))

        // Our action
        action = Action("") {
          model = queueEditor.orElse(ftEditor).orElse(stEditor).orElse(model)
        }

        icon = SharedIcons.ICON_CLOCK

      }

      // Band 3 time label
      object label extends Label with Bound[Proposal, ProposalClass] {
        val lens = Proposal.proposalClass

        // Configure
        horizontalAlignment = Alignment.Left
        border = BorderFactory.createEmptyBorder(0, 3, 0, 0)
        minimumSize = (270, preferredSize.height)

        // On refresh, set our text
        override def refresh(m: Option[ProposalClass]): Unit = {
          text = ~m.map {
            case q: QueueProposalClass => ~q.band3request.map { r =>
              "%1.2f %s (%1.2f %s minimum) requested".format(
                r.time.value,
                r.time.units.value,
                r.minTime.value,
                r.minTime.units.value)
            }
            case _                     => ""
          }
        }

      }

    }

    // Visitors label
    lazy val visitorsLabel = dvLabel("Visitors:") {
      case _: ClassicalProposalClass => true
    }

    // Visitors control set
    object visitors extends BorderPanel with Bound.Self[Proposal] {

      override def children = List(button, label)

      add(button, West)
      add(label, Center)

      override def refresh(m: Option[Proposal]): Unit = {
        visible = ~m.map(_.proposalClass).map {
          case _: ClassicalProposalClass => true
          case _                         => false
        }
      }

      object button extends Button with Bound.Self[Proposal] {
        val cpcLens = Proposal.proposalClass

        action = Action("") {
          model.foreach { p =>
            p.proposalClass match {
              case c: ClassicalProposalClass =>
                val vs0 = c.visitors.flatMap(_(p))
                val all = p.investigators.all
                val sel = all.map(vs0.contains)
                VisitorSelector.open(all.zip(sel), canEdit, view) foreach { vs1 =>
                  model = Some(cpcLens.set(p, c.copy(visitors = vs1.map(_.ref))))
                }
              case _                         => // nop, should never happen
            }
          }
        }
        icon = SharedIcons.ICON_USER
      }

      object label extends Label with Bound.Self[Proposal] {
        horizontalAlignment = Alignment.Left
        border = BorderFactory.createEmptyBorder(0, 3, 0, 0)

        override def refresh(m: Option[Proposal]): Unit = {
          text = ~m.map { p =>
            p.proposalClass match {
              case c: ClassicalProposalClass =>
                c.visitors.flatMap(_(p)) match {
                  case Nil => "None selected."
                  case is => is.mkString(", ")
                }
              case _                         => ""
            }
          }
        }
      }

    }

    // FT Reviewer label
    lazy val ftReviewerLabel = dvLabel("Reviewer:") {
      case _: FastTurnaroundProgramClass => true
      case _ => false
    }

    def reviewers(m: Option[Proposal], phDRequired:Boolean = false):List[Investigator] = ~(for {
        p <- m
      } yield for {
          i <- p.investigators.all
          if !phDRequired || i.status == InvestigatorStatus.PH_D
        } yield i)

    // FIXME
    def currentPi(m: Option[Proposal]):PrincipalInvestigator = (for {
        p <- m
      } yield p.investigators.pi).get

    def updateComboBoxModel(peer: JComboBox[Investigator], reviewers: List[Investigator]): Unit = {
      // Re-populate combo-box model
      val cbModel = new DefaultComboBoxModel[Investigator]()
      reviewers.foreach(cbModel.addElement)
      peer.setModel(cbModel)
    }

    def selectedInvestigator(model: ComboBoxModel[Investigator]): Option[Investigator] = model.getSelectedItem match {
      case x: Investigator => Some(x)
      case _               => None
    }

    case class InvestigatorRenderer[A](delegate: ListView.Renderer[A], combo: ComboBox[A]) extends ListView.Renderer[A] {

      def componentFor(list: ListView[_ <: A], isSelected: Boolean, focused: Boolean, a: A, index: Int): Component = {
        val c = delegate.componentFor(list, isSelected, focused, a, index)
        if (reviewer.peer.getItemCount > 0) {
          c.peer.asInstanceOf[JLabel].setText(stringValue(a))
        }
        c
      }

      def stringValue(a: Any): String = try {
        a match {
          case e: Investigator => e.fullName
          case _               => ""
        }
      } catch {
        case e: Throwable => ""
      }

    }

    // Reviewer control set
    object reviewer extends ComboBox[Investigator](Seq.empty) with Bound.Self[Proposal] {
      border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
      val cbModel = new DefaultComboBoxModel[Investigator]()
      peer.setModel(cbModel)
      updateP1Model(model.map(_.investigators.pi))

      val pcLens = Proposal.proposalClass
      renderer = InvestigatorRenderer(renderer, this)

      def currentReviewer(m: Option[Proposal]): Option[Investigator] = for {
          p <- m
          f @ FastTurnaroundProgramClass(_, _, _, _, _, _, Some(r), _, _, _) <- Some(p.proposalClass)
        } yield r

      override def refresh(m:Option[Proposal]): Unit = {
        enabled = canEdit
        visible = ~m.map(_.proposalClass).map {
          case _: FastTurnaroundProgramClass => true
          case _                             => false
        }
        val pi = currentPi(model)

        val validReviewers = reviewers(m, phDRequired = false)

        val currentlySelected = for {
            i                <- Option(peer.getModel.getSelectedItem).collect {
                                  case i: Investigator => i
                                }
            if validReviewers.contains(i)
          } yield i
        val reviewer = Option(currentReviewer(m).getOrElse(pi) match {
            case i if validReviewers.map(_.ref).contains(i.ref) => validReviewers.find(_.ref == i.ref).get
            case _                                              => pi
          })
        updateComboBoxModel(peer, validReviewers)

        val selected = if (reviewer != currentlySelected) reviewer else currentlySelected
        selected.foreach{ i =>
          peer.getModel.setSelectedItem(i)
          updateP1Model(Some(i))
        }
      }

      selection.reactions += {
          case SelectionChanged(_) =>
            updateP1Model(Option(selection.item))
        }

      def updateP1Model(selection: Option[Investigator]): Unit = {
        model.foreach { p => p.proposalClass match {
          case f: FastTurnaroundProgramClass =>
            val validMentors = reviewers(model, phDRequired = true)
            // If the reviewer has a PhD set mentor to None
            val m = if (hasPhD(f)) None else f.mentor.orElse(validMentors.headOption)
            val reviewer = selection
            val pc = FastTurnaroundProgramClass.reviewerAndMentor.set(f, (reviewer, m))
            model = Some(pcLens.set(p, pc))
          case _ => // ignore
        }}
      }

      def hasPhD(f: FastTurnaroundProgramClass):Boolean =
        selectedInvestigator(peer.getModel).exists(r => r.status == InvestigatorStatus.PH_D)
    }

    // FT Mentor label
    lazy val ftMentorLabel = dvLabel("Mentor:") {
      case f:FastTurnaroundProgramClass => !reviewer.hasPhD(f)
      case _                            => false
    }

    // Mentor control set
    object mentor extends ComboBox[Investigator](Seq.empty) with Bound.Self[Proposal] {
      border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
      val cbModel = new DefaultComboBoxModel[Investigator]()
      peer.setModel(cbModel)

      val pcLens = Proposal.proposalClass
      renderer = InvestigatorRenderer(renderer, this)

      def currentMentor(m: Option[Proposal]): Option[Investigator] = for {
          p                                                                  <- m
          f @ FastTurnaroundProgramClass(_, _, _, _, _, _, _, Some(m), _, _) <- Some(p.proposalClass)
        } yield m

      def updateP1Model(selection: Option[Investigator]): Unit = {
        model.foreach { p => p.proposalClass match {
          case f:FastTurnaroundProgramClass =>
            val pc = FastTurnaroundProgramClass.mentor.set(f, selection)
            model = Some(pcLens.set(p, pc))
          case _                            => // ignore
        }}
      }

      override def refresh(m:Option[Proposal]): Unit = {
        enabled = canEdit
        visible = ~m.map(_.proposalClass).map {
          case f: FastTurnaroundProgramClass => !reviewer.hasPhD(f)
          case _                             => false
        }

        val validMentors = reviewers(m, phDRequired = true)
        val mentor = currentMentor(m) match {
                  case Some(i) if validMentors.map(_.ref).contains(i.ref) => validMentors.find(_.ref == i.ref)
                  case _                                                  => validMentors.headOption
                }
        val currentlySelected = for {
            i                <- Option(peer.getModel.getSelectedItem).collect {
                                  case i: Investigator => i
                                }
            if validMentors.contains(i)
          } yield i
        updateComboBoxModel(peer, validMentors)
        if (mentor.isDefined && mentor != currentlySelected) {
          mentor.foreach(peer.getModel.setSelectedItem)
          updateP1Model(mentor)
        } else {
          if (currentlySelected != mentor || currentlySelected.isEmpty) {
            updateP1Model(currentlySelected)
          } else {
            currentlySelected.foreach(peer.getModel.setSelectedItem)
          }
        }
      }

      selection.reactions += {
          case SelectionChanged(_) => updateP1Model(Option(selection.item))
        }
    }

    // FT Partner Affiliation label
    lazy val partnerAffiliationLabel = dvLabel("PI's Affiliation:") {
      case f:FastTurnaroundProgramClass => true
      case _                            => false
    }

    // Partner affiliation combo box
    object partnerAffiliation extends ComboBox[String](Partners.ftPartners.map(_._2)) with Bound.Self[Proposal] {
      maximumRowCount = Partners.ftPartners.size

      val pcLens = Proposal.proposalClass

      def currentAffiliation(pOpt: Option[Proposal]): FtPartner = pOpt.map(_.proposalClass).flatMap {
        case f: FastTurnaroundProgramClass => f.partnerAffiliation
        case _                             => None
      }

      override def refresh(m:Option[Proposal]): Unit = {
        enabled = canEdit
        visible = ~m.map(_.proposalClass).map {
          case _: FastTurnaroundProgramClass => true
          case _                             => false
        }

        // Use the current affiliation, if it exists, and otherwise, the affiliation associated with the
        // PI's institution.
        selection.item = {
          val currentAffiliate          = currentAffiliation(m)
          lazy val institutionAffiliate = Institutions.institution2Ngo(currentPi(m).address)
          val partner = if (m.exists(_.meta.overrideAffiliate) && currentAffiliate.isDefined) currentAffiliate else institutionAffiliate
          Partners.ftPartners.toMap.getOrElse(partner, Partners.NoPartnerAffiliation)
        }
      }

      selection.reactions += {
        case SelectionChanged(_) =>
          val selectedAffiliate    = Partners.toPartner(selection.item)
          val currentAffiliate     = currentAffiliation(model)
          val institutionAffiliate = Institutions.institution2Ngo(currentPi(model).address)

          if (currentAffiliate =/= selectedAffiliate) {
            selection.item = Partners.ftPartners.toMap.getOrElse(selectedAffiliate, Partners.NoPartnerAffiliation)
            updateP1Model(selectedAffiliate)
          }

          val overrideAffiliate = selectedAffiliate =/= institutionAffiliate
          if (model.exists(_.meta.overrideAffiliate != overrideAffiliate)) updateP1ModelOverrideAffiliate(overrideAffiliate)
      }

      def updateP1ModelOverrideAffiliate(overrideAffiliate: Boolean): Unit = {
        model.foreach { p =>
          val p0 = (Proposal.meta andThen Meta.overrideAffiliate).set(p, overrideAffiliate)
          model = Some(p0)
        }
      }

      def updateP1Model(partner: FtPartner): Unit = {
        model.foreach {p => p.proposalClass match {
          case f: FastTurnaroundProgramClass =>
            val pc = FastTurnaroundProgramClass.affiliation.set(f, partner)
            model = Some(pcLens.set(p, pc))
          case _ => // ignore
        }}
      }
    }

    // Special label
    lazy val specialLabel = dvLabel("Proposal Type:") {
      case _:SpecialProposalClass => true
    }

    // Special combo box
    object special extends ComboBox(SpecialProposalType.values.toSeq) with ValueRenderer[SpecialProposalType] with Bound[Proposal, ProposalClass] {

      val lens = Proposal.proposalClass

      // A lens to allow us to set the type in one shot
      val spt: Lens[SpecialProposalClass, SpecialProposalType] = SpecialProposalClass.sub andThen SpecialSubmission.specialType

      override def refresh(m:Option[ProposalClass]): Unit = {
        enabled = canEdit
        m.foreach {
          case s:SpecialProposalClass =>
            selection.item = s.sub.specialType
            visible = true
          case _                      => visible = false
        }
      }

      selection.reactions += {
        case SelectionChanged(_) =>
          model.foreach {
            case s:SpecialProposalClass => model = Some(spt.set(s, selection.item))
            case _                      => // ignore
          }
      }

    }

    // Special time label
    lazy val specialTimeLabel = dvLabel("Time:") {
      case _: SpecialProposalClass        => true
      case _: LargeProgramClass           => true
      case _: FastTurnaroundProgramClass  => true
      case _: SubaruIntensiveProgramClass => true
    }

    // Special time control set
    object specialTime extends BorderPanel with Bound[Proposal, ProposalClass] {

      val lens = Proposal.proposalClass

      // We have bound children
      override def children = List(edit, label)

      // Add our children, defined below
      add(edit, West)
      add(label, Center)

      // On refresh we update visibility
      override def refresh(m:Option[ProposalClass]): Unit = {
        edit.enabled = canEdit
        visible = ~m.map {
          case _: SpecialProposalClass        => true
          case _: LargeProgramClass           => true
          case _: FastTurnaroundProgramClass  => true
          case _: SubaruIntensiveProgramClass => true
          case _                              => false
        }
      }

      // special time edit button
      object edit extends Button with Bound.Self[ProposalClass] {button =>

        // A lens to allow us to set the type in one shot
        val sr = SpecialProposalClass.sub andThen SpecialSubmission.request

        // A lens to allow us to set the request
        val lp = LargeProgramClass.sub andThen LargeProgramSubmission.request

        // A lens to allow us to set the request
        val sip = SubaruIntensiveProgramClass.sub andThen SubaruIntensiveProgramSubmission.request

        // A lens to allow us to set the request
        val ft = FastTurnaroundProgramClass.sub andThen FastTurnaroundSubmission.request

        def spRequest = for {
            s @ SpecialProposalClass(_, _, _, sub, _, _, _) <- model
            (req, _, _)                            <- SubmissionRequestEditor.open(sub.request, None, Nil, None, button)
          } yield Some(sr.set(s, req))

        def lpRequest = for {
            l @ LargeProgramClass(_, _, _, sub, _, _, _) <- model
            req                                    <- LargeSubmissionRequestEditor.open(sub.request, button)
          } yield Some(lp.set(l, req))

        def sipRequest = for {
            l @ SubaruIntensiveProgramClass(_, _, _, _, sub) <- model
            req                                              <- LargeSubmissionRequestEditor.open(sub.request, button)
          } yield Some(sip.set(l, req))

        def ftRequest = for {
            l @ FastTurnaroundProgramClass(_, _, _, sub, _, _, _, _, _, _) <- model
            req                                                         <- SubmissionRequestEditor.open(sub.request, None, Nil, None, button)
          } yield Some(ft.set(l, req._1))

        // Our action
        action = Action("") {
          model = spRequest.orElse(lpRequest).orElse(ftRequest).orElse(sipRequest).getOrElse(model)
        }

        icon = SharedIcons.ICON_CLOCK

      }

      // special time label
      object label extends Label with Bound.Self[ProposalClass] {

        // Configure
        horizontalAlignment = Alignment.Left
        border = BorderFactory.createEmptyBorder(0, 3, 0, 0)

        // On refresh, set our text
        override def refresh(m:Option[ProposalClass]): Unit = {
          text = ~m.map {
            case s: SpecialProposalClass        => formatLabel(s.sub.request)
            case f: FastTurnaroundProgramClass  => formatLabel(f.sub.request)
            case l: LargeProgramClass           => formatLabel(l.sub.request)
            case s: SubaruIntensiveProgramClass => formatLabel(s.sub.request)
            case _                              => ""
          }
        }

        def formatLabel(r: SubmissionRequest): String =
          f"${r.time.value}%1.2f ${r.time.units.value} (${r.minTime.value}%1.2f ${r.minTime.units.value} minimum) requested"
      }

    }

    // Request type label
    lazy val partnerTypeLabel = dvLabel("Request Type:") {
      case _: QueueProposalClass     => true
      case _: ClassicalProposalClass => true
    }

    // Request type selector. This one is kind of complicated, especially after REL-3943, which requires filtering
    // out CHF for classical observations.
    object partnerType extends ComboBox[PartnerType.Value](PartnerType.values.toSeq) with Bound[Proposal, ProposalClass] {

      val lens = Proposal.proposalClass

      // We have local state for all alternatives
      var localGemini:List[NgoSubmission] = Nil
      var localKeck   = ExchangeSubmission(SubmissionRequest.empty, None, ExchangePartner.KECK, InvestigatorRef.empty)
      var localSubaru = ExchangeSubmission(SubmissionRequest.empty, None, ExchangePartner.SUBARU, InvestigatorRef.empty)
      var localCFH    = ExchangeSubmission(SubmissionRequest.empty, None, ExchangePartner.CFH, InvestigatorRef.empty)

      override def refresh(m:Option[ProposalClass]): Unit = {

        // Enabled?
        enabled = canEdit

        // Update items.
        deafTo(selection)
          def updateComboBoxModel(includeCFH: Boolean): Unit = {
            val newModel = new DefaultComboBoxModel[PartnerType.Value]()
            newModel.addElement(PartnerType.GeminiPartner)
            if (includeCFH)
              newModel.addElement(PartnerType.ExchangeCFH)
            newModel.addElement(PartnerType.ExchangeKeck)
            newModel.addElement(PartnerType.ExchangeSubaru)
            peer.setModel(newModel)
          }
          m.foreach { p =>
            val item = peer.getSelectedItem
            val wasCFH = peer.getSelectedItem == PartnerType.ExchangeCFH
            updateComboBoxModel(header.proposalClass.peer.getSelectedItem != ProposalClassSelection.Classical)
            if (proposalClass.peer.getSelectedItem == ProposalClassSelection.Classical && wasCFH)
              peer.setSelectedIndex(0)
            else peer.setSelectedItem(item)
        }

        // Update visibility
        visible = ~m.map {
          case _:QueueProposalClass     => true
          case _:ClassicalProposalClass => true
          case _                        => false
        }

        // Update local state
        m.foreach {
          case QueueProposalClass(_, _, _, Left(ngos), _, _, _, _, _)                                       => localGemini = ngos
          case QueueProposalClass(_, _, _, Right(e), _, _, _, _, _) if e.partner == ExchangePartner.KECK    => localKeck = e
          case QueueProposalClass(_, _, _, Right(e), _, _, _, _, _) if e.partner == ExchangePartner.SUBARU  => localSubaru = e
          case QueueProposalClass(_, _, _, Right(e), _, _, _, _, _) if e.partner == ExchangePartner.CFH     => localCFH = e
          case ClassicalProposalClass(_, _, _, Left(ngos), _, _, _, _)                                      => localGemini = ngos
          case ClassicalProposalClass(_, _, _, Right(e), _, _, _, _) if e.partner == ExchangePartner.KECK   => localKeck = e
          case ClassicalProposalClass(_, _, _, Right(e), _, _, _, _) if e.partner == ExchangePartner.SUBARU => localSubaru = e
          case ClassicalProposalClass(_, _, _, Right(e), _, _, _, _) if e.partner == ExchangePartner.CFH    => localGemini = Nil
          case e: ExchangeProposalClass                                                                     => localGemini = e.subs
          case _                                                                                            => // ignore
        }

        // Update our selected item
        selection.item = m.map {
          case QueueProposalClass(_, _, _, Left(_), _, _, _, _, _)                                          => GeminiPartner
          case QueueProposalClass(_, _, _, Right(e), _, _, _, _, _) if e.partner == ExchangePartner.KECK    => ExchangeKeck
          case QueueProposalClass(_, _, _, Right(e), _, _, _, _, _) if e.partner == ExchangePartner.SUBARU  => ExchangeSubaru
          case QueueProposalClass(_, _, _, Right(e), _, _, _, _, _) if e.partner == ExchangePartner.CFH     => ExchangeCFH
          case ClassicalProposalClass(_, _, _, Left(_), _, _, _, _)                                         => GeminiPartner
          case ClassicalProposalClass(_, _, _, Right(e), _, _, _, _) if e.partner == ExchangePartner.KECK   => ExchangeKeck
          case ClassicalProposalClass(_, _, _, Right(e), _, _, _, _) if e.partner == ExchangePartner.SUBARU => ExchangeSubaru
          case ClassicalProposalClass(_, _, _, Right(e), _, _, _, _) if e.partner == ExchangePartner.CFH    => GeminiPartner
          case _: ExchangeProposalClass                                                                     => GeminiPartner
          case _: SpecialProposalClass                                                                      => GeminiPartner
          case _: LargeProgramClass                                                                         => GeminiPartner
          case _: SubaruIntensiveProgramClass                                                               => ExchangeSubaru
          case _: FastTurnaroundProgramClass                                                                => GeminiPartner
        }.getOrElse(PartnerType.GeminiPartner)

        listenTo(selection)
      }

      // When the user changes the selection...
      selection.reactions += {
        case SelectionChanged(_) if (selection.item != null) =>
          model = model.map {
            case q:QueueProposalClass     =>
              selection.item match {
                case GeminiPartner  => q.copy(subs = Left(localGemini))
                case ExchangeKeck   => q.copy(subs = Right(localKeck))
                case ExchangeSubaru => q.copy(subs = Right(localSubaru))
                case ExchangeCFH    => q.copy(subs = Right(localCFH))
              }
            case c:ClassicalProposalClass =>
              selection.item match {
                case GeminiPartner  => c.copy(subs = Left(localGemini))
                case ExchangeKeck   => c.copy(subs = Right(localKeck))
                case ExchangeSubaru => c.copy(subs = Right(localSubaru))
                case _              => c.copy(subs = Left(localGemini))
              }
            case e:ExchangeProposalClass  =>
              selection.item match {
                case GeminiPartner => e.copy(subs = localGemini)
                case _             => e // should never happen
              }
            case p:ProposalClass   => p
          }
      }

    }

  }

  sealed trait PSWrapper {
    def ps:PartnerSubmission[_,_]
    def isReal:Boolean
  }
  case class Real[A,B <: PartnerSubmission[A,B]](ps:PartnerSubmission[A,B]) extends PSWrapper {
    val isReal = true
  }
  case class Placeholder[A,B <: PartnerSubmission[A,B]](ps:PartnerSubmission[A,B]) extends PSWrapper {
    val isReal = false
  }

  // Our list
  object list extends SimpleListViewer[Proposal, Proposal, PSWrapper] {

    // Bound
    val lens = Lens.lensId[Proposal]

    // Adjust visibility on refresh
    override def refresh(m:Option[Proposal]) {
      super.refresh(m) // important
      visible = ~m.map(_.proposalClass).map {
        case _: SpecialProposalClass        => false
        case _: LargeProgramClass           => false
        case _: FastTurnaroundProgramClass  => false
        case _: SubaruIntensiveProgramClass => false
        case _                              => true
      }
    }

    object columns extends Enumeration {
      val Partner, Time = Value
      val MinTime = Value("Min Time")
      val Lead = Value
    }

    import columns._

    def columnWidth = {
      case Partner => (200, Int.MaxValue)
      case Time    => (100, 100)
      case MinTime => (100, 100)
      case Lead    => (100, 100)
    }

    def size(p:Proposal) = subs(p.proposalClass).length

    def elementAt(p:Proposal, i:Int) = subs(p.proposalClass)(i)

    def icon(s:PSWrapper) = {
      case Partner => PartnersFlags.flag.get(s.ps.partner).orNull
      case _       => null
    }

    def text(s:PSWrapper) = {
      case Partner => Partners.name.get(s.ps.partner).orNull
      case Lead    if  s.isReal => model.flatMap(s.ps.partnerLead(_)).map(i => "%s %s".format(i.firstName, i.lastName)).orNull
      case Time    if  s.isReal => formatTime(s.ps.request.time)
      case MinTime if  s.isReal => formatTime(s.ps.request.minTime)
    }

    def formatTime(t:TimeAmount) = "%3.2f %s".format(t.value, t.units.value)

    onDoubleClick { psw => edit(psw.ps) }

    def edit(s: PartnerSubmission[_,_]) {
      for {
        p <- model
        if canEdit //
        (r, i, x) <- SubmissionRequestEditor.open(s.request, Some(s.partner), p.investigators.all, s.partnerLead(p), view)
        // HACK: (request, lead, remove)
      } {

        val pc = if (x) {

          // We are removing an NGO partner. This can happen only for some proposal classes. Exchange partners can
          // never be removed because there's always exactly one of them.
          (s.partner, p.proposalClass) match {
            case (n: NgoPartner, q: QueueProposalClass)          => q.copy(subs = Left(remove(n, ~q.subs.left.toOption)))
            case (n: NgoPartner, c: ClassicalProposalClass)      => c.copy(subs = Left(remove(n, ~c.subs.left.toOption)))
            case (n: NgoPartner, e: ExchangeProposalClass)       => e.copy(subs = remove(n, e.subs))
            case _                                               => p.proposalClass // shouldn't happen
          }

        } else {

          // We are replacing an arbitrary submission
          (s.partner, p.proposalClass) match {
            case (e: ExchangePartner, q: QueueProposalClass)     => q.copy(subs = Right(ExchangeSubmission(r, None, e, i.ref)))
            case (n: NgoPartner, q: QueueProposalClass)          => q.copy(subs = Left(replace(n, i.ref, r, ~q.subs.left.toOption)))
            case (e: ExchangePartner, c: ClassicalProposalClass) => c.copy(subs = Right(ExchangeSubmission(r, None, e, i.ref)))
            case (n: NgoPartner, c: ClassicalProposalClass)      => c.copy(subs = Left(replace(n, i.ref, r, ~c.subs.left.toOption)))
            case (n: NgoPartner, e: ExchangeProposalClass)       => e.copy(subs = replace(n, i.ref, r, e.subs))
            case _                                               => p.proposalClass // shouldn't happen
          }
        }

        model = Some(p.copy(proposalClass = pc))

      }
    }

    def remove(p:NgoPartner, subs:List[NgoSubmission]) = subs.filterNot(_.partner == p)

    def replace(p:NgoPartner, i:InvestigatorRef, r:SubmissionRequest, subs:List[NgoSubmission]) =
      if (subs.exists(_.partner == p))
        subs.map {n => if (n.partner == p) n.copy(partnerLead = i, request = r) else n}
      else NgoSubmission(r, None, p, i) :: subs

    def subs(p: ProposalClass): List[PSWrapper] = p match {
      case QueueProposalClass(_, _, _, Left(ngos), _, _, _, _, _)   => all(ngos)
      case QueueProposalClass(_, _, _, Right(exch), _, _, _, _, _)  => List(Real(exch))
      case ClassicalProposalClass(_, _, _, Left(ngos), _, _, _, _)  => all(ngos)
      case ClassicalProposalClass(_, _, _, Right(exch), _, _, _, _) => List(Real(exch))
      case e: ExchangeProposalClass                                 => all(e.subs)
      case _: SpecialProposalClass                                  => Nil
      case _: LargeProgramClass                                     => Nil
      case _: SubaruIntensiveProgramClass                           => Nil
      case _: FastTurnaroundProgramClass                            => Nil
    }

    def all(ngos:List[NgoSubmission]):List[PSWrapper] = {
      val ps = NgoPartner.values.toList.sortBy(_.value)
      val subs = ps.map(p => ngos.find(_.partner == p)).zip(ps.map(NgoSubmission(SubmissionRequest.empty, None, _, InvestigatorRef.empty)))
      subs.map(p => p._1.map(Real(_)).getOrElse(Placeholder(p._2)))
    }

  }

  ///
  /// HELPERS
  ///

  // A class of labels whose visibility is based on a partial function
  def dvLabel(s:String)(f:PartialFunction[ProposalClass, Boolean]) =
    new Label(s) with Bound[Proposal, ProposalClass] {
      val lens = Proposal.proposalClass
      override def refresh(m:Option[ProposalClass]) {
        visible = ~m.map(~f.lift(_))
      }
    }


  /** Opens the editor for the given submission. */
  def editSubmissionTime(sub: Submission) {
    sub match {
      case _: SpecialSubmission                 => header.specialTime.edit.doClick()
      case _: LargeProgramSubmission            => header.specialTime.edit.doClick()
      case _: SubaruIntensiveProgramSubmission  => header.specialTime.edit.doClick()
      case _: FastTurnaroundSubmission          => header.specialTime.edit.doClick()
      case ps: PartnerSubmission[_,_]           => list.edit(ps)
    }
  }

  def editProposalClass(): Unit = {
    header.proposalClass.requestFocusInWindow()
  }

  def editBand3Time() {
    header.band3.edit.doClick()
  }
}
